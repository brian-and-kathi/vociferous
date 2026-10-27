package com.brian.dialbuddy.data

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.brian.dialbuddy.domain.IgnoredSender
import com.brian.dialbuddy.domain.Notice
import com.brian.dialbuddy.domain.decodeIgnored
import com.brian.dialbuddy.domain.decodeNotices
import com.brian.dialbuddy.domain.encodeIgnored
import com.brian.dialbuddy.domain.encodeNotices
import com.brian.dialbuddy.domain.recordDismissed
import com.brian.dialbuddy.domain.recordPosted
import com.brian.dialbuddy.domain.removeNotices
import com.brian.dialbuddy.domain.syncNotices
import com.brian.dialbuddy.domain.withIgnored
import com.brian.dialbuddy.domain.withoutIgnored
import com.brian.dialbuddy.telecom.NoticeListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NoticeLog {
    private val lock = Any()
    private val active = HashMap<String, StatusBarNotification>()
    private val _records = MutableStateFlow<List<Notice>>(emptyList())
    val records: StateFlow<List<Notice>> = _records.asStateFlow()
    private val _ignored = MutableStateFlow<List<IgnoredSender>>(emptyList())
    val ignored: StateFlow<List<IgnoredSender>> = _ignored.asStateFlow()
    private var file: File? = null
    private var ignoredFile: File? = null

    fun attach(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val target = File(context.applicationContext.filesDir, "notice-log.txt")
            file = target
            val raw = try {
                if (target.exists()) target.readText() else ""
            } catch (_: IOException) {
                ""
            }
            _records.value = decodeNotices(raw)
            val ignoredTarget = File(context.applicationContext.filesDir, "ignored-notices.txt")
            ignoredFile = ignoredTarget
            val ignoredRaw = try {
                if (ignoredTarget.exists()) ignoredTarget.readText() else ""
            } catch (_: IOException) {
                ""
            }
            _ignored.value = decodeIgnored(ignoredRaw)
        }
    }

    fun posted(context: Context, sbn: StatusBarNotification) {
        attach(context)
        if (isIgnored(sbn.packageName)) return
        val notice = describe(context, sbn) ?: return
        synchronized(lock) {
            if (isIgnoredLocked(notice.packageName)) return
            active[notice.key] = sbn
            write(recordPosted(_records.value, notice, context.packageName, ignoredPackagesLocked()))
        }
    }

    fun dismissed(key: String, at: Long) {
        synchronized(lock) {
            active.remove(key)
            if (file == null) return
            write(recordDismissed(_records.value, key, at))
        }
    }

    fun sync(context: Context, posted: List<StatusBarNotification>) {
        attach(context)
        val live = ArrayList<Notice>()
        val current = HashMap<String, StatusBarNotification>()
        val skipped = ignoredPackages()
        for (sbn in posted) {
            if (sbn.packageName in skipped) continue
            val notice = describe(context, sbn) ?: continue
            live.add(notice)
            current[notice.key] = sbn
        }
        synchronized(lock) {
            active.clear()
            active.putAll(current)
            write(
                syncNotices(
                    _records.value,
                    live,
                    System.currentTimeMillis(),
                    context.packageName,
                    ignoredPackagesLocked(),
                ),
            )
        }
    }

    fun ignore(packageName: String, appName: String) {
        if (packageName.isBlank()) return
        synchronized(lock) {
            if (file == null) return
            writeIgnored(withIgnored(_ignored.value, packageName, appName))
            val keys = _records.value.filter { it.packageName == packageName }.map { it.key }.toSet()
            for (key in keys) active.remove(key)
            if (keys.isNotEmpty()) write(removeNotices(_records.value, keys))
        }
    }

    fun restore(packageName: String) {
        if (packageName.isBlank()) return
        synchronized(lock) {
            if (file == null) return
            writeIgnored(withoutIgnored(_ignored.value, packageName))
        }
    }

    fun remove(keys: Set<String>) {
        if (keys.isEmpty()) return
        val live = synchronized(lock) {
            val posted = keys.filter { active.containsKey(it) }
            for (key in keys) active.remove(key)
            if (file != null) write(removeNotices(_records.value, keys))
            posted
        }
        for (key in live) NoticeListener.cancel(key)
    }

    fun open(context: Context, key: String): Boolean {
        val pending = synchronized(lock) { active[key]?.notification?.contentIntent }
        val packageName = synchronized(lock) {
            active[key]?.packageName ?: _records.value.firstOrNull { it.key == key }?.packageName
        } ?: return false
        if (pending != null && send(pending)) return true
        return launchApp(context, packageName)
    }

    private fun send(pending: PendingIntent): Boolean {
        return try {
            pending.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun describe(context: Context, sbn: StatusBarNotification): Notice? {
        if (sbn.packageName == context.packageName || sbn.key.isNullOrBlank()) return null
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty().take(200)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val body = (if (text.isNotEmpty()) text else big).take(500)
        return Notice(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appLabel(context, sbn.packageName).take(80),
            title = title,
            text = body,
            postedAt = sbn.postTime,
            updatedAt = System.currentTimeMillis(),
            dismissed = false,
            iconName = saveAvatar(context, sbn),
        )
    }

    fun avatarFor(context: Context, notice: Notice): Bitmap? {
        val name = notice.iconName
        if (!name.isNullOrBlank()) {
            val file = File(context.applicationContext.filesDir, "notice-icons/$name")
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            if (decoded != null) return decoded
        }
        return appBitmap(context, notice.packageName)
    }

    private fun saveAvatar(context: Context, sbn: StatusBarNotification): String? {
        return try {
            val bitmap = avatarBitmap(context, sbn) ?: return null
            val dir = File(context.applicationContext.filesDir, "notice-icons")
            if (!dir.exists() && !dir.mkdirs()) return null
            val name = iconFileName(sbn.key)
            FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        } catch (_: Exception) {
            null
        }
    }

    private fun avatarBitmap(context: Context, sbn: StatusBarNotification): Bitmap? {
        val notification = sbn.notification
        val extras = notification.extras
        val icons = ArrayList<Icon>()
        @Suppress("DEPRECATION")
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(
            extras.getParcelableArray(Notification.EXTRA_MESSAGES),
        )
        messages?.mapNotNullTo(icons) { it.senderPerson?.icon }
        @Suppress("DEPRECATION")
        (extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON) as? Person)?.icon?.let { icons.add(it) }
        @Suppress("DEPRECATION")
        val people = extras.getParcelableArrayList<Person>(Notification.EXTRA_PEOPLE_LIST)
        people?.mapNotNullTo(icons) { it.icon }
        notification.getLargeIcon()?.let { icons.add(it) }
        notification.smallIcon?.let { icons.add(it) }
        for (icon in icons) {
            bitmapFromIcon(context, icon)?.let { return scale(it) }
        }
        shortcutBitmap(context, sbn)?.let { return scale(it) }
        return appBitmap(context, sbn.packageName)?.let { scale(it) }
    }

    private fun bitmapFromIcon(context: Context, icon: Icon): Bitmap? {
        return try {
            val uriBacked = icon.type == Icon.TYPE_URI || icon.type == Icon.TYPE_URI_ADAPTIVE_BITMAP
            if (uriBacked) {
                context.contentResolver.openInputStream(icon.uri)?.use { BitmapFactory.decodeStream(it) }
            } else {
                icon.loadDrawable(context)?.toBitmap(96, 96)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shortcutBitmap(context: Context, sbn: StatusBarNotification): Bitmap? {
        val shortcutId = sbn.notification.shortcutId ?: return null
        return try {
            val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
            val query = LauncherApps.ShortcutQuery()
                .setPackage(sbn.packageName)
                .setShortcutIds(listOf(shortcutId))
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED,
                )
            val shortcut = launcher.getShortcuts(query, Process.myUserHandle())?.firstOrNull() ?: return null
            launcher.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
                ?.toBitmap(96, 96)
        } catch (_: Exception) {
            null
        }
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= 96 && bitmap.height <= 96) return bitmap
        return Bitmap.createScaledBitmap(bitmap, 96, 96, true)
    }

    private fun appBitmap(context: Context, packageName: String): Bitmap? {
        return try {
            appIcon(context, packageName)?.toBitmap(96, 96)
        } catch (_: Exception) {
            null
        }
    }

    private fun appIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun iconFileName(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) } + ".png"
    }

    private fun appLabel(context: Context, packageName: String): String {
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun isIgnored(packageName: String): Boolean = synchronized(lock) { isIgnoredLocked(packageName) }

    private fun isIgnoredLocked(packageName: String): Boolean =
        _ignored.value.any { it.packageName == packageName }

    private fun ignoredPackages(): Set<String> = synchronized(lock) { ignoredPackagesLocked() }

    private fun ignoredPackagesLocked(): Set<String> = _ignored.value.map { it.packageName }.toSet()

    private fun writeIgnored(next: List<IgnoredSender>) {
        if (next == _ignored.value) return
        val target = ignoredFile ?: return
        try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            val encoded = encodeIgnored(next)
            tmp.writeText(encoded)
            if (!tmp.renameTo(target)) {
                target.writeText(encoded)
                tmp.delete()
            }
        } catch (_: IOException) {
        }
        _ignored.value = next
    }

    private fun write(next: List<Notice>) {
        if (next == _records.value) return
        val dropped = _records.value.filter { old -> next.none { it.key == old.key } }
        deleteIcons(dropped)
        val target = file ?: return
        try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            val encoded = encodeNotices(next)
            tmp.writeText(encoded)
            if (!tmp.renameTo(target)) {
                target.writeText(encoded)
                tmp.delete()
            }
        } catch (_: IOException) {
        }
        _records.value = next
    }

    private fun deleteIcons(notices: List<Notice>) {
        val root = file?.parentFile ?: return
        for (notice in notices) {
            val name = notice.iconName ?: continue
            File(File(root, "notice-icons"), name).delete()
        }
    }
}
