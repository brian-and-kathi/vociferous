package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicemailRulesTest {
    private fun entry(
        id: Long,
        seconds: Int?,
        archived: Boolean = false,
        deleted: Boolean = false,
        number: String? = "555",
    ) = VoicemailEntry(id, number, seconds, 0L, archived, deleted)

    @Test
    fun emptyMeansShorterThanThresholdAndStillInTheMailbox() {
        assertTrue(entry(1, 2).isEmpty(3))
        assertFalse(entry(1, 3).isEmpty(3))
        assertFalse(entry(1, 2, archived = true).isEmpty(3))
        assertFalse(entry(1, 2, deleted = true).isEmpty(3))
        assertFalse(entry(1, null).isEmpty(3))
        assertFalse(entry(1, -1).isEmpty(3))
    }

    @Test
    fun thresholdClampsToOneThroughTwenty() {
        assertEquals(1, clampThreshold(0))
        assertEquals(20, clampThreshold(99))
        assertEquals(3, clampThreshold(3))
    }

    @Test
    fun singleDeleteFindsARecognizedMessageThatBulkDeleteSkips() {
        val recognized = entry(7, 40, number = "5550100")
        val removed = entry(9, 40, number = "5550100", deleted = true)
        assertEquals(recognized, voicemailById(listOf(recognized, removed), 7))
        assertNull(voicemailById(listOf(removed), 9))
        assertTrue(bulkVoicemails(listOf(recognized), 3, setOf("5550100")).isEmpty())
    }

    @Test
    fun mailboxListsTheOldestMessageFirst() {
        val older = VoicemailEntry(1, "555", 4, 10L, false)
        val newer = VoicemailEntry(2, "555", 4, 30L, false)
        val removed = VoicemailEntry(3, "555", 4, 40L, false, deleted = true)
        assertEquals(
            listOf(1L, 2L),
            chronologicalVoicemails(listOf(newer, removed, older)).map { it.id },
        )
    }

    @Test
    fun bulkDeleteSkipsRecognizedNumbers() {
        val entries = listOf(
            entry(1, 1, number = "5550100"),
            entry(2, 1, number = "5550199"),
            entry(3, 30, number = "5550199"),
        )
        val bulk = bulkVoicemails(entries, 3, setOf("5550100"))
        assertEquals(listOf(2L), bulk.map { it.id })
    }

    @Test
    fun deletionTallyFollowsTheButtonThatRemovedTheMessage() {
        assertEquals(DeletionCounts(known = 1), tallyDeletions(listOf(true), bulkButton = false))
        assertEquals(DeletionCounts(known = 1), tallyDeletions(listOf(true), bulkButton = true))
        assertEquals(DeletionCounts(unknown = 1), tallyDeletions(listOf(false), bulkButton = false))
        assertEquals(DeletionCounts(bulk = 2), tallyDeletions(listOf(false, false), bulkButton = true))
        assertEquals(
            DeletionCounts(known = 1, bulk = 2),
            tallyDeletions(listOf(true, false, false), bulkButton = true),
        )
    }

    @Test
    fun deletionStampsRoundTripAndStayInsideTheWindow() {
        val recent = DeletionStamp(5_000L, DeletionCounts(known = 3, unknown = 2, bulk = 7))
        val stamps = listOf(recent)
        assertEquals(stamps, parseDeletionStamps(formatDeletionStamps(stamps)))
        assertEquals(listOf(recent), parseDeletionStamps("5000:3:2:7:9"))
        assertEquals(emptyList<DeletionStamp>(), parseDeletionStamps("1000:2;bad;3:0;x:2;4:-1:0:1;6000:0:0:0:4"))
        val now = 10_000L
        val window = deletedInWindow(stamps, now, 6_000L)
        assertEquals(DeletionCounts(known = 3, unknown = 2, bulk = 7), window)
        assertEquals(window.known.toLong() + window.unknown + window.bulk, window.total)
        assertEquals(12L, nextDeletedTotal(10, 2))
    }

    @Test
    fun negativeTotalsAreRejected() {
        try {
            nextDeletedTotal(-1, 1)
            throw AssertionError("expected total rejection")
        } catch (error: IllegalArgumentException) {
            assertEquals("total cannot be negative", error.message)
        }
    }
}
