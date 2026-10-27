package com.brian.dialbuddy.domain

const val NOTICE_LIMIT = 400
const val OWN_PACKAGE = "com.brian.dialbuddy"

private const val FIELD = '\u001f'

data class Notice(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val updatedAt: Long,
    val dismissed: Boolean,
    val iconName: String? = null,
)

fun recordPosted(
    current: List<Notice>,
    incoming: Notice,
    ownPackage: String = OWN_PACKAGE,
    ignored: Set<String> = emptySet(),
): List<Notice> {
    if (incoming.packageName == ownPackage || incoming.key.isBlank() || incoming.packageName in ignored) {
        return current
    }
    val previous = current.find { it.key == incoming.key }
    val next = if (previous == null) {
        incoming.copy(dismissed = false)
    } else {
        incoming.copy(
            postedAt = previous.postedAt,
            dismissed = false,
            iconName = incoming.iconName ?: previous.iconName,
        )
    }
    return capNotices(current.filter { it.key != incoming.key } + next)
}

fun recordDismissed(current: List<Notice>, key: String, at: Long): List<Notice> {
    return current.map { notice ->
        if (notice.key == key && !notice.dismissed) {
            notice.copy(dismissed = true, updatedAt = maxOf(notice.updatedAt, at))
        } else {
            notice
        }
    }
}

fun syncNotices(
    current: List<Notice>,
    live: List<Notice>,
    now: Long,
    ownPackage: String = OWN_PACKAGE,
    ignored: Set<String> = emptySet(),
): List<Notice> {
    val kept = if (ignored.isEmpty()) current else current.filter { it.packageName !in ignored }
    val liveKeys = live.map { it.key }.toSet()
    var next = kept
    for (existing in kept) {
        if (!existing.dismissed && existing.key !in liveKeys) {
            next = recordDismissed(next, existing.key, now)
        }
    }
    for (notice in live) {
        next = recordPosted(next, notice, ownPackage, ignored)
    }
    return next
}

data class IgnoredSender(
    val packageName: String,
    val appName: String,
)

fun withIgnored(current: List<IgnoredSender>, packageName: String, appName: String): List<IgnoredSender> {
    if (packageName.isBlank()) return current
    val name = appName.ifBlank { packageName }
    return current.filter { it.packageName != packageName } + IgnoredSender(packageName, name)
}

fun withoutIgnored(current: List<IgnoredSender>, packageName: String): List<IgnoredSender> {
    return current.filter { it.packageName != packageName }
}

fun encodeIgnored(senders: List<IgnoredSender>): String {
    return senders.joinToString("\n") { sender ->
        listOf(sender.packageName, sender.appName).joinToString(FIELD.toString()) { escapeField(it) }
    }
}

fun decodeIgnored(raw: String): List<IgnoredSender> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        if (line.isEmpty()) return@mapNotNull null
        val parts = splitFields(line)
        if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
        IgnoredSender(parts[0], parts[1].ifBlank { parts[0] })
    }.toList()
}

data class NoticeApp(
    val packageName: String,
    val appName: String,
    val count: Int,
)

fun noticeApps(notices: List<Notice>): List<NoticeApp> {
    return notices
        .groupBy { it.packageName }
        .map { (packageName, rows) ->
            val newest = rows.maxBy { it.updatedAt }
            NoticeApp(packageName, newest.appName.ifBlank { packageName }, rows.size) to newest.updatedAt
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

fun removeNotices(current: List<Notice>, keys: Set<String>): List<Notice> {
    if (keys.isEmpty()) return current
    return current.filter { it.key !in keys }
}

fun capNotices(current: List<Notice>, limit: Int = NOTICE_LIMIT): List<Notice> {
    return current.sortedByDescending { it.updatedAt }.take(limit)
}

fun encodeNotices(notices: List<Notice>): String {
    return notices.joinToString("\n") { notice ->
        listOf(
            notice.key,
            notice.packageName,
            notice.appName,
            notice.title,
            notice.text,
            notice.postedAt.toString(),
            notice.updatedAt.toString(),
            if (notice.dismissed) "1" else "0",
            notice.iconName.orEmpty(),
        ).joinToString(FIELD.toString()) { escapeField(it) }
    }
}

fun decodeNotices(raw: String): List<Notice> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        if (line.isEmpty()) return@mapNotNull null
        val parts = splitFields(line)
        if (parts.size != 8 && parts.size != 9) return@mapNotNull null
        Notice(
            key = parts[0],
            packageName = parts[1],
            appName = parts[2],
            title = parts[3],
            text = parts[4],
            postedAt = parts[5].toLongOrNull() ?: return@mapNotNull null,
            updatedAt = parts[6].toLongOrNull() ?: return@mapNotNull null,
            dismissed = parts[7] == "1",
            iconName = parts.getOrNull(8)?.takeIf { it.isNotBlank() },
        )
    }.toList()
}

private fun escapeField(value: String): String = buildString {
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            FIELD -> append("\\s")
            else -> append(ch)
        }
    }
}

private fun splitFields(line: String): List<String> {
    val fields = ArrayList<String>(8)
    val current = StringBuilder()
    var index = 0
    while (index < line.length) {
        val ch = line[index]
        if (ch == '\\' && index + 1 < line.length) {
            when (line[index + 1]) {
                '\\' -> current.append('\\')
                'n' -> current.append('\n')
                'r' -> current.append('\r')
                's' -> current.append(FIELD)
                else -> current.append(line[index + 1])
            }
            index += 2
            continue
        }
        if (ch == FIELD) {
            fields.add(current.toString())
            current.clear()
        } else {
            current.append(ch)
        }
        index++
    }
    fields.add(current.toString())
    return fields
}
