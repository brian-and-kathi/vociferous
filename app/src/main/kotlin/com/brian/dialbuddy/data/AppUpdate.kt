package com.brian.dialbuddy.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.ReleaseOffer
import com.brian.dialbuddy.domain.isInstallableUpgrade
import com.brian.dialbuddy.domain.isNewerRelease
import com.brian.dialbuddy.domain.isReleaseCheckDue
import com.brian.dialbuddy.domain.releaseOffer
import com.brian.dialbuddy.domain.shouldPromptForUpdate
import java.io.File
import kotlin.concurrent.thread

object AppUpdate {
    sealed class Check {
        class Update(val offer: ReleaseOffer) : Check()
        data object Current : Check()
        data object Failed : Check()
    }

    private const val APK_TYPE = "application/vnd.android.package-archive"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val waiters = mutableListOf<(ReleaseOffer?) -> Unit>()
    private val promptLock = Any()
    private var refreshInFlight = false
    private var promptedVersion: String? = null
    private var installInFlight = false

    fun offer(context: Context): ReleaseOffer? {
        val stored = AppStorage(context).readUpdateOffer() ?: return null
        if (!isNewerRelease(installedVersionName(context), stored.versionName)) return null
        return stored
    }

    fun dismiss(context: Context, versionName: String) {
        AppStorage(context).writeDismissedUpdate(versionName)
    }

    fun claimPrompt(context: Context, offer: ReleaseOffer): Boolean {
        synchronized(promptLock) {
            val dismissed = AppStorage(context).readDismissedUpdate()
            if (!shouldPromptForUpdate(offer.versionName, dismissed, promptedVersion)) return false
            promptedVersion = offer.versionName
            return true
        }
    }

    fun refreshAsync(activity: Activity, onResult: (ReleaseOffer?) -> Unit) {
        val app = activity.applicationContext
        fun deliver(offer: ReleaseOffer?) {
            onMain {
                if (activity.isFinishing || activity.isDestroyed) return@onMain
                onResult(offer)
            }
        }
        val checkedAt = AppStorage(app).readUpdateCheckedAt()
        if (!isReleaseCheckDue(checkedAt, System.currentTimeMillis())) {
            deliver(offer(app))
            return
        }
        synchronized(waiters) {
            waiters.add { result -> deliver(result) }
            if (refreshInFlight) return
            refreshInFlight = true
        }
        thread(name = "vociferous-update") {
            try {
                fetchAndStore(app)
            } catch (_: Exception) {
            }
            val result = offer(app)
            val pending = synchronized(waiters) {
                refreshInFlight = false
                waiters.toList().also { waiters.clear() }
            }
            pending.forEach { it(result) }
        }
    }

    fun checkNow(activity: Activity, onResult: (Check) -> Unit) {
        val app = activity.applicationContext
        thread(name = "vociferous-update") {
            val result = try {
                if (!fetchAndStore(app)) Check.Failed
                else offer(app)?.let { Check.Update(it) } ?: Check.Current
            } catch (_: Exception) {
                Check.Failed
            }
            onMain {
                if (activity.isFinishing || activity.isDestroyed) return@onMain
                onResult(result)
            }
        }
    }

    fun startInstall(activity: Activity, offer: ReleaseOffer) {
        if (installInFlight) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow, Toast.LENGTH_LONG).show()
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    ),
                )
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
            }
            return
        }
        installInFlight = true
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        thread(name = "vociferous-update") {
            val file = try {
                ReleaseLookup.downloadApk(offer.apkUrl, activity.applicationContext.cacheDir)
            } catch (_: Exception) {
                null
            }
            onMain {
                installInFlight = false
                if (activity.isFinishing || activity.isDestroyed) return@onMain
                if (file == null || !downloadedApkCanUpgrade(activity, file) || !launchInstaller(activity, file)) {
                    Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchAndStore(context: Context): Boolean {
        return when (val result = ReleaseLookup.queryLatest()) {
            is LatestRelease.Found -> {
                val installed = installedVersionName(context)
                val offer = releaseOffer(result.release.version, result.release.downloadUrl, installed)
                if (offer == null && isNewerRelease(installed, result.release.version)) return false
                AppStorage(context).writeUpdateCheck(System.currentTimeMillis(), offer)
                true
            }
            LatestRelease.Unavailable -> {
                AppStorage(context).writeUpdateCheckedAt(System.currentTimeMillis())
                false
            }
            LatestRelease.Unreadable -> false
        }
    }

    private fun downloadedApkCanUpgrade(context: Context, file: File): Boolean {
        val archived = archiveInfo(context, file) ?: return false
        val installed = installedPackage(context)
        return isInstallableUpgrade(
            archived.packageName,
            archived.longVersionCode,
            installed.longVersionCode,
        )
    }

    private fun launchInstaller(activity: Activity, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
            activity.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun onMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun installedVersionName(context: Context): String {
        return installedPackage(context).versionName.orEmpty()
    }

    private fun installedPackage(context: Context): PackageInfo {
        val manager = context.packageManager
        val name = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(name, 0)
        }
    }

    private fun archiveInfo(context: Context, file: File): PackageInfo? {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageArchiveInfo(file.absolutePath, 0)
        }
    }
}
