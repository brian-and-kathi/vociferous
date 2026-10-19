package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnknownRingtoneTest {
    @Test
    fun knownCallersKeepTheSystemRingtone() {
        assertFalse(useUnknownRingtone(incoming = true, number = "555", contactsAllowed = true, inContacts = true))
    }

    @Test
    fun unrecognizedNumbersUseTheOtherRingtone() {
        assertTrue(useUnknownRingtone(incoming = true, number = "555", contactsAllowed = true, inContacts = false))
    }

    @Test
    fun blankNumbersUseTheOtherRingtone() {
        assertTrue(useUnknownRingtone(incoming = true, number = "  ", contactsAllowed = true, inContacts = false))
        assertTrue(useUnknownRingtone(incoming = true, number = "", contactsAllowed = false, inContacts = false))
    }

    @Test
    fun outgoingCallsAndUnreadContactsKeepTheSystemRingtone() {
        assertFalse(useUnknownRingtone(incoming = false, number = "555", contactsAllowed = true, inContacts = false))
        assertFalse(useUnknownRingtone(incoming = true, number = "555", contactsAllowed = false, inContacts = false))
    }

    @Test
    fun silentAndDoNotDisturbStayQuiet() {
        assertEquals(
            RingPlayback(sound = false, vibrate = false),
            ringPlayback(RingerMode.Normal, Interruption.Other, priorityAllowsAnyone = true, vibrateWhenRinging = true),
        )
        assertEquals(
            RingPlayback(sound = false, vibrate = false),
            ringPlayback(RingerMode.Silent, Interruption.All, priorityAllowsAnyone = true, vibrateWhenRinging = true),
        )
        assertEquals(
            RingPlayback(sound = false, vibrate = false),
            ringPlayback(RingerMode.Normal, Interruption.Priority, priorityAllowsAnyone = false, vibrateWhenRinging = true),
        )
    }

    @Test
    fun vibrateModeSkipsTheTone() {
        assertEquals(
            RingPlayback(sound = false, vibrate = true),
            ringPlayback(RingerMode.Vibrate, Interruption.All, priorityAllowsAnyone = false, vibrateWhenRinging = false),
        )
    }

    @Test
    fun normalRingFollowsTheVibrateSetting() {
        assertEquals(
            RingPlayback(sound = true, vibrate = true),
            ringPlayback(RingerMode.Normal, Interruption.All, priorityAllowsAnyone = false, vibrateWhenRinging = true),
        )
        assertEquals(
            RingPlayback(sound = true, vibrate = false),
            ringPlayback(RingerMode.Normal, Interruption.Priority, priorityAllowsAnyone = true, vibrateWhenRinging = false),
        )
    }
}
