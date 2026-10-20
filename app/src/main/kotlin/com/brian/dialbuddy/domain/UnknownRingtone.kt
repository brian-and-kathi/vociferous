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

// The other ringtone plays only for an incoming unrecognized number whose
// area code is one the user saved. Known callers, outgoing calls, numbers
// with no area code, and calls that could not be checked against contacts
// keep the system ringtone.
fun useUnknownRingtone(
    incoming: Boolean,
    number: String,
    contactsAllowed: Boolean,
    inContacts: Boolean,
    areaCodes: Set<String>,
): Boolean {
    if (!incoming || !contactsAllowed || inContacts || areaCodes.isEmpty()) return false
    val code = areaCodeOf(number) ?: return false
    return code in areaCodes
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
