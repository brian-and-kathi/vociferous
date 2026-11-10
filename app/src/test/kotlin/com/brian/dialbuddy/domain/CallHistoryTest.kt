package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallHistoryTest {
    @Test
    fun contactLookupWinsOverTheNameSavedOnTheCall() {
        val contacts = mapOf("555" to "Ada")
        assertEquals("Ada", recognizedCallerName("555", "Old Name", contacts))
        assertNull(recognizedCallerName("555", "Old Name", emptyMap()))
    }

    @Test
    fun savedNameIsUsedWhenContactsCannotBeRead() {
        assertEquals("Ada", recognizedCallerName("555", "Ada", null))
        assertNull(recognizedCallerName("555", "  ", null))
    }

    @Test
    fun blankNumbersStayUnrecognized() {
        assertNull(recognizedCallerName("", "Ada", mapOf("" to "Ada")))
        assertNull(recognizedCallerName("   ", null, null))
    }

    @Test
    fun pagesListTheOldestCallFirst() {
        val calls = listOf(
            call(1, "555", 100),
            call(2, "999", 300),
            call(3, "555", 200),
        )
        val contacts = mapOf("555" to "Ada")
        assertEquals(listOf(1L, 3L), callsForPage(calls, contacts, recognized = true).map { it.id })
        assertEquals(listOf(2L), callsForPage(calls, contacts, recognized = false).map { it.id })
    }

    @Test
    fun durationClockHidesZeroAndPadsSeconds() {
        assertNull(callDurationClock(0))
        assertNull(callDurationClock(-3))
        assertEquals("0:08", callDurationClock(8))
        assertEquals("1:05", callDurationClock(65))
        assertEquals("1:02:03", callDurationClock(3723))
    }

    private fun call(id: Long, number: String, at: Long) = CallRecord(
        id = id,
        number = number,
        cachedName = null,
        kind = CallKind.Incoming,
        dateMillis = at,
        durationSeconds = 0,
    )
}
