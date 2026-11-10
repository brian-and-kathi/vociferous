package com.brian.dialbuddy.domain

// Comfort-noise and empty MPEG frames stay under this peak. Real speech does not.
const val AUDIBLE_PEAK = 300
const val AUDIBLE_HOLD_MILLIS = 200

data class PcmLevel(
    val sampleRate: Int,
    val totalFrames: Int,
    val audibleFrames: Int,
    val exhausted: Boolean,
)

class PcmAccumulator(
    var sampleRate: Int,
    var channels: Int = 1,
    private val floor: Int = AUDIBLE_PEAK,
) {
    var totalFrames: Int = 0
        private set
    var audibleFrames: Int = 0
        private set

    fun adoptFormat(sampleRate: Int, channels: Int) {
        if (totalFrames != 0) return
        if (sampleRate > 0) this.sampleRate = sampleRate
        if (channels > 0) this.channels = channels
    }

    fun accept(interleaved: ShortArray) {
        val width = channels.coerceAtLeast(1)
        var index = 0
        while (index + width <= interleaved.size) {
            var framePeak = 0
            for (channel in 0 until width) {
                val magnitude = kotlin.math.abs(interleaved[index + channel].toInt())
                if (magnitude > framePeak) framePeak = magnitude
            }
            if (framePeak >= floor) audibleFrames += 1
            totalFrames += 1
            index += width
        }
    }

    fun level(exhausted: Boolean) = PcmLevel(sampleRate, totalFrames, audibleFrames, exhausted)
}

// Null means the file was not decoded, so the carrier length still stands.
// False means the payload is empty or only comfort noise.
fun PcmLevel.hasAudibleAudio(): Boolean? {
    if (sampleRate <= 0) {
        return if (exhausted && totalFrames == 0) false else null
    }
    val heard = audibleFrames.toLong() * 1000L / sampleRate >= AUDIBLE_HOLD_MILLIS
    if (heard) return true
    if (!exhausted) return null
    return false
}

enum class VoicemailPlay {
    Play,
    Silent,
    Unavailable,
}

// No stored recording is silence, same as a file that decodes to nothing.
// A file that could not be decoded still tries to play.
fun voicemailPlay(hasContent: Boolean?, audible: Boolean?): VoicemailPlay = when {
    hasContent == null -> VoicemailPlay.Unavailable
    !hasContent -> VoicemailPlay.Silent
    audible == false -> VoicemailPlay.Silent
    else -> VoicemailPlay.Play
}

// The provider duration counts a missing or silent file as several seconds.
// Only a recording that is present and audible keeps that length.
fun shownDurationSeconds(carrierSeconds: Int?, level: PcmLevel?, hasContent: Boolean = true): Int? {
    if (!hasContent) return 0
    val measured = level
    return when (measured?.hasAudibleAudio()) {
        null -> carrierSeconds
        false -> 0
        true -> when {
            carrierSeconds != null && carrierSeconds > 0 -> carrierSeconds
            measured.exhausted && measured.sampleRate > 0 && measured.totalFrames > 0 -> {
                val millis = measured.totalFrames.toLong() * 1000L / measured.sampleRate
                ((millis + 500L) / 1000L).toInt().coerceAtLeast(1)
            }
            else -> carrierSeconds
        }
    }
}
