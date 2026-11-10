package com.brian.dialbuddy.telecom

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import com.brian.dialbuddy.domain.PcmAccumulator
import com.brian.dialbuddy.domain.PcmLevel
import com.brian.dialbuddy.domain.hasAudibleAudio
import java.nio.ByteOrder

class VoicemailAudioProbe(context: Context) {
    private val app = context.applicationContext

    fun measure(uri: Uri): PcmLevel? {
        val statedLength = try {
            app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        } catch (_: Exception) {
            return null
        }
        if (statedLength == null) return null
        if (statedLength == 0L) {
            return PcmLevel(sampleRate = 0, totalFrames = 0, audibleFrames = 0, exhausted = true)
        }
        return decode(uri)
    }

    private fun decode(uri: Uri): PcmLevel? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(app, uri, null)
            val track = audioTrack(extractor) ?: return PcmLevel(0, 0, 0, exhausted = true)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            return drain(extractor, decoder, format)
        } catch (_: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, decoder: MediaCodec, format: MediaFormat): PcmLevel? {
        val accumulator = PcmAccumulator(
            sampleRate = format.optionalInt(MediaFormat.KEY_SAMPLE_RATE) ?: 0,
            channels = format.optionalInt(MediaFormat.KEY_CHANNEL_COUNT) ?: 1,
        )
        var encoding = format.optionalInt(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val deadline = SystemClock.elapsedRealtime() + DECODE_BUDGET_MS
        while (!outputDone) {
            if (SystemClock.elapsedRealtime() > deadline) {
                val partial = accumulator.level(exhausted = false)
                return if (partial.hasAudibleAudio() == true) partial else null
            }
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex) ?: return null
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val output = decoder.outputFormat
                    accumulator.adoptFormat(
                        output.optionalInt(MediaFormat.KEY_SAMPLE_RATE) ?: accumulator.sampleRate,
                        output.optionalInt(MediaFormat.KEY_CHANNEL_COUNT) ?: 1,
                    )
                    encoding = output.optionalInt(MediaFormat.KEY_PCM_ENCODING) ?: encoding
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT) return null
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> {
                    if (outIndex < 0) continue
                    if (encoding != AudioFormat.ENCODING_PCM_16BIT) return null
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val raw = decoder.getOutputBuffer(outIndex) ?: return null
                        raw.position(info.offset)
                        raw.limit(info.offset + info.size)
                        val pcm = raw.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                        val samples = ShortArray(pcm.remaining())
                        pcm.get(samples)
                        accumulator.accept(samples)
                        if (accumulator.level(exhausted = false).hasAudibleAudio() == true) {
                            decoder.releaseOutputBuffer(outIndex, false)
                            return accumulator.level(exhausted = false)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        return accumulator.level(exhausted = true)
    }

    private fun audioTrack(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return index
        }
        return null
    }

    private fun MediaFormat.optionalInt(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val DECODE_BUDGET_MS = 5_000L
    }
}
