package com.brian.dialbuddy.domain

data class VoicemailEntry(
    val id: Long,
    val number: String?,
    val durationSeconds: Int?,
    val dateMillis: Long,
    val archived: Boolean,
    val deleted: Boolean = false,
)

data class DeletionCounts(
    val known: Int = 0,
    val unknown: Int = 0,
    val bulk: Int = 0,
) {
    val total: Long
        get() = known.toLong() + unknown + bulk

    operator fun plus(other: DeletionCounts) = DeletionCounts(
        known = known + other.known,
        unknown = unknown + other.unknown,
        bulk = bulk + other.bulk,
    )
}

data class DeletionStamp(
    val atMillis: Long,
    val counts: DeletionCounts,
)

const val DEFAULT_THRESHOLD_SECONDS = 3
const val MIN_THRESHOLD_SECONDS = 1
const val MAX_THRESHOLD_SECONDS = 20
const val MILLIS_PER_DAY = 86_400_000L
const val WINDOW_WEEK_MILLIS = 604_800_000L
const val WINDOW_MONTH_MILLIS = 2_592_000_000L
const val WINDOW_YEAR_MILLIS = 31_536_000_000L

fun clampThreshold(seconds: Int): Int =
    seconds.coerceIn(MIN_THRESHOLD_SECONDS, MAX_THRESHOLD_SECONDS)

fun VoicemailEntry.isEmpty(thresholdSeconds: Int): Boolean {
    if (archived || deleted) return false
    val duration = durationSeconds ?: return false
    if (duration < 0) return false
    return duration < thresholdSeconds
}

fun presentVoicemails(entries: List<VoicemailEntry>): List<VoicemailEntry> =
    entries.filterNot { it.deleted }

fun chronologicalVoicemails(entries: List<VoicemailEntry>): List<VoicemailEntry> =
    presentVoicemails(entries).sortedBy { it.dateMillis }

fun voicemailById(entries: List<VoicemailEntry>, id: Long): VoicemailEntry? =
    presentVoicemails(entries).firstOrNull { it.id == id }

fun emptyVoicemails(entries: List<VoicemailEntry>, thresholdSeconds: Int): List<VoicemailEntry> {
    val threshold = clampThreshold(thresholdSeconds)
    return entries.filter { it.isEmpty(threshold) }
}

fun VoicemailEntry.qualifiesForBulkDelete(thresholdSeconds: Int, recognized: Boolean): Boolean {
    if (recognized) return false
    return isEmpty(clampThreshold(thresholdSeconds))
}

fun bulkVoicemails(
    entries: List<VoicemailEntry>,
    thresholdSeconds: Int,
    recognizedNumbers: Set<String>,
): List<VoicemailEntry> {
    val threshold = clampThreshold(thresholdSeconds)
    return entries.filter { entry ->
        val number = entry.number?.trim().orEmpty()
        val recognized = number.isNotEmpty() && number in recognizedNumbers
        entry.qualifiesForBulkDelete(threshold, recognized)
    }
}

fun tallyDeletions(recognized: List<Boolean>, bulkButton: Boolean): DeletionCounts {
    var known = 0
    var unknown = 0
    var bulk = 0
    for (isRecognized in recognized) {
        when {
            isRecognized -> known++
            bulkButton -> bulk++
            else -> unknown++
        }
    }
    return DeletionCounts(known = known, unknown = unknown, bulk = bulk)
}

fun nextDeletedTotal(previousTotal: Long, deletedNow: Int): Long {
    require(previousTotal >= 0) { "total cannot be negative" }
    require(deletedNow >= 0) { "deletedNow cannot be negative" }
    return deletedNow + previousTotal
}

fun deletedInWindow(stamps: List<DeletionStamp>, nowMillis: Long, windowMillis: Long): DeletionCounts {
    require(windowMillis >= 0) { "window cannot be negative" }
    val start = nowMillis - windowMillis
    var sum = DeletionCounts()
    for (stamp in stamps) {
        if (stamp.counts.total > 0 && stamp.atMillis in start..nowMillis) {
            sum += stamp.counts
        }
    }
    return sum
}

fun retainRecentStamps(stamps: List<DeletionStamp>, nowMillis: Long): List<DeletionStamp> {
    val start = nowMillis - WINDOW_YEAR_MILLIS
    return stamps.filter { it.counts.total > 0 && it.atMillis >= start }
}

fun parseDeletionStamps(stored: String): List<DeletionStamp> {
    if (stored.isBlank()) return emptyList()
    return stored.split(';').mapNotNull { piece ->
        val parts = piece.split(':')
        val at = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val numbers = parts.drop(1).map { it.toIntOrNull() ?: return@mapNotNull null }
        if (numbers.size !in 3..4 || numbers.any { it < 0 }) return@mapNotNull null
        val counts = DeletionCounts(known = numbers[0], unknown = numbers[1], bulk = numbers[2])
        if (counts.total <= 0) return@mapNotNull null
        DeletionStamp(at, counts)
    }
}

fun formatDeletionStamps(stamps: List<DeletionStamp>): String =
    stamps.joinToString(";") { stamp ->
        val counts = stamp.counts
        "${stamp.atMillis}:${counts.known}:${counts.unknown}:${counts.bulk}"
    }

