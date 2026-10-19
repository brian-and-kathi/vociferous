package com.brian.dialbuddy.domain

enum class RingerMode {
    Silent,
    Vibrate,
    Normal,
}

enum class Interruption {
    All,
    Priority,
    Other,
}

data class RingPlayback(
    val sound: Boolean,
    val vibrate: Boolean,
)

// Blank numbers are unrecognized even when contacts cannot be read.
// A non-blank number keeps the system ringtone when lookup was not allowed.
fun useUnknownRingtone(
    incoming: Boolean,
    number: String,
    contactsAllowed: Boolean,
    inContacts: Boolean,
): Boolean {
    if (!incoming) return false
    if (number.isBlank()) return true
    if (!contactsAllowed) return false
    return !inContacts
}

fun ringPlayback(
    mode: RingerMode,
    interruption: Interruption,
    priorityAllowsAnyone: Boolean,
    vibrateWhenRinging: Boolean,
): RingPlayback {
    val allowed = when (interruption) {
        Interruption.All -> true
        Interruption.Priority -> priorityAllowsAnyone
        Interruption.Other -> false
    }
    if (!allowed || mode == RingerMode.Silent) return RingPlayback(sound = false, vibrate = false)
    if (mode == RingerMode.Vibrate) return RingPlayback(sound = false, vibrate = true)
    return RingPlayback(sound = true, vibrate = vibrateWhenRinging)
}
