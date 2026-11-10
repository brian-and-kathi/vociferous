package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicemailAudioTest {
    @Test
    fun silentPayloadDoesNotKeepASeveralSecondCarrierDuration() {
        val level = PcmAccumulator(sampleRate = 8_000).apply {
            accept(ShortArray(8_000 * 6))
        }.level(exhausted = true)
        assertEquals(false, level.hasAudibleAudio())
        assertEquals(0, shownDurationSeconds(carrierSeconds = 6, level = level))
    }

    @Test
    fun comfortNoiseStaysSilentAndSpeechKeepsTheCarrierLength() {
        val noise = PcmAccumulator(sampleRate = 8_000).apply {
            accept(ShortArray(8_000 * 5) { 200 })
        }.level(exhausted = true)
        assertFalse(noise.hasAudibleAudio()!!)
        assertEquals(0, shownDurationSeconds(5, noise))

        val speech = PcmAccumulator(sampleRate = 8_000).apply {
            accept(ShortArray(8_000) { 4_000 })
        }.level(exhausted = true)
        assertTrue(speech.hasAudibleAudio()!!)
        assertEquals(6, shownDurationSeconds(6, speech))
    }

    @Test
    fun aShortClickIsNotARecording() {
        val click = PcmAccumulator(sampleRate = 8_000).apply {
            accept(ShortArray(800) { 12_000 })
            accept(ShortArray(8_000 * 4))
        }.level(exhausted = true)
        assertEquals(false, click.hasAudibleAudio())
        assertEquals(0, shownDurationSeconds(5, click))
    }

    @Test
    fun anUnreadFileKeepsTheCarrierDuration() {
        assertNull(shownDurationSeconds(null, null))
        assertEquals(7, shownDurationSeconds(7, null))
        val partial = PcmAccumulator(sampleRate = 16_000).apply {
            accept(ShortArray(16_000 / 10))
        }.level(exhausted = false)
        assertNull(partial.hasAudibleAudio())
        assertEquals(7, shownDurationSeconds(7, partial))
    }

    @Test
    fun audibleAudioWithoutACarrierDurationUsesTheDecodedLength() {
        val level = PcmAccumulator(sampleRate = 8_000).apply {
            accept(ShortArray(8_000 * 2) { 5_000 })
        }.level(exhausted = true)
        assertEquals(2, shownDurationSeconds(null, level))
        assertEquals(2, shownDurationSeconds(0, level))
    }

    @Test
    fun oneLoudChannelMakesAStereoFrameAudible() {
        val frames = 8_000 / 2
        val interleaved = ShortArray(frames * 2)
        for (frame in 0 until frames) {
            interleaved[frame * 2] = 0
            interleaved[frame * 2 + 1] = 2_000
        }
        val level = PcmAccumulator(sampleRate = 8_000, channels = 2).apply {
            accept(interleaved)
        }.level(exhausted = true)
        assertEquals(true, level.hasAudibleAudio())
        assertEquals(frames, level.audibleFrames)
    }

    @Test
    fun noContentDoesNotKeepASeveralSecondCarrierDurationOrStartPlayback() {
        assertEquals(0, shownDurationSeconds(4, null, hasContent = false))
        assertEquals(0, shownDurationSeconds(13, null, hasContent = false))
        assertEquals(VoicemailPlay.Silent, voicemailPlay(hasContent = false, audible = null))
        assertEquals(VoicemailPlay.Silent, voicemailPlay(hasContent = true, audible = false))
        assertEquals(VoicemailPlay.Play, voicemailPlay(hasContent = true, audible = true))
        assertEquals(VoicemailPlay.Play, voicemailPlay(hasContent = true, audible = null))
        assertEquals(VoicemailPlay.Unavailable, voicemailPlay(hasContent = null, audible = null))
    }

    @Test
    fun anEmptyFileIsSilent() {
        val empty = PcmLevel(sampleRate = 0, totalFrames = 0, audibleFrames = 0, exhausted = true)
        assertEquals(false, empty.hasAudibleAudio())
        assertEquals(0, shownDurationSeconds(8, empty))
    }
}
