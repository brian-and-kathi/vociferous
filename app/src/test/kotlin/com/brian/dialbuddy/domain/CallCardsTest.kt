package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CallCardsTest {
    @Test
    fun dialerStaysLeftOfOngoingCallsAndIncomingCallsComeLast() {
        val calls = listOf(
            "ringing-a" to true,
            "active" to false,
            "holding" to false,
            "ringing-b" to true,
        )
        assertEquals(
            listOf("active", "holding", "ringing-a", "ringing-b"),
            orderedCallCards(calls) { it.second }.map { it.first },
        )
    }

    @Test
    fun reorderKeepsTheCallTheUserIsViewing() {
        val keys = listOf("active", "ringing")
        assertEquals(2, callPageAfterReorder(keys, settledKey = "ringing", currentPage = 1))
        assertEquals(0, callPageAfterReorder(keys, settledKey = null, currentPage = 0))
    }

    @Test
    fun aCallThatLeftClampsThePage() {
        assertEquals(1, callPageAfterReorder(listOf("active"), settledKey = "gone", currentPage = 4))
        assertEquals(0, callPageAfterReorder(emptyList(), settledKey = null, currentPage = 3))
    }
}
