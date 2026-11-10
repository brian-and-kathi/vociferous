package com.brian.dialbuddy.domain

enum class CallKind {
    Incoming,
    Outgoing,
    Missed,
    Rejected,
    Blocked,
    Voicemail,
    Other,
}

data class CallRecord(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val kind: CallKind,
    val dateMillis: Long,
    val durationSeconds: Long,
    val cachedPhoto: String? = null,
)

// contacts null: contact lookup was not allowed, so the name saved on the call is used.
// An empty map means lookup ran and this number is not a contact.
fun recognizedCallerName(
    number: String,
    cachedName: String?,
    contacts: Map<String, String>?,
): String? {
    if (number.isBlank()) return null
    if (contacts != null) return contacts[number]?.takeIf { it.isNotBlank() }
    return cachedName?.takeIf { it.isNotBlank() }
}

fun callsForPage(
    calls: List<CallRecord>,
    contacts: Map<String, String>?,
    recognized: Boolean,
): List<CallRecord> {
    return calls
        .filter { record ->
            val known = recognizedCallerName(record.number, record.cachedName, contacts) != null
            known == recognized
        }
        .sortedBy { it.dateMillis }
}

fun callDurationClock(seconds: Long): String? {
    if (seconds <= 0) return null
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    val short = "${minutes}:${rest.toString().padStart(2, '0')}"
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}" else short
}
