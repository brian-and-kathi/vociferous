package com.brian.dialbuddy.telecom

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.VoicemailContract
import java.util.concurrent.Executors

class VoicemailPlayer(
    context: Context,
    private val store: VoicemailStore,
    speakerOn: Boolean,
    private val onPlaying: (Long?) -> Unit,
    private val onProgress: (Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val audio = app.getSystemService(AudioManager::class.java)
    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val cueAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var speakerOn = speakerOn
    private var generation = 0
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var observer: ContentObserver? = null
    private var poll: Runnable? = null
    private var clock: Runnable? = null
    private var cueTask: Runnable? = null
    private var cueTrack: AudioTrack? = null
    private var previousMode: Int? = null
    private var preparing = false

    fun play(id: Long) {
        if (Calls.state.value.calls.isNotEmpty()) {
            onError(app.getString(com.brian.dialbuddy.R.string.play_during_call))
            return
        }
        halt(clearPlaying = false)
        onPlaying(id)
        val token = generation
        io.execute {
            val media = try {
                store.media(id)
            } catch (_: VoicemailAccessDenied) {
                null
            }
            main.post {
                if (token != generation) return@post
                when {
                    media == null -> fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
                    media.hasContent -> startPlayer(id, media.sourcePackage, token)
                    requestDownload(id, media.sourcePackage) -> watch(id, media.sourcePackage, token)
                    else -> fail(app.getString(com.brian.dialbuddy.R.string.download_failed))
                }
            }
        }
    }

    fun setSpeaker(on: Boolean) {
        speakerOn = on
        player?.let { applyRoute(it) }
    }

    fun stop() {
        halt(clearPlaying = true)
    }

    private fun halt(clearPlaying: Boolean) {
        generation += 1
        preparing = false
        clearWatch()
        stopClock()
        stopCue()
        releasePlayer()
        restoreRoute()
        abandonFocus()
        if (clearPlaying) {
            onPlaying(null)
            onProgress(0f)
        }
    }

    fun release() {
        stop()
        io.shutdown()
    }

    private fun startPlayer(id: Long, sourcePackage: String?, token: Int) {
        if (token != generation) return
        if (!requestFocus()) {
            fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
            return
        }
        preparing = true
        clearWatch()
        releasePlayer()
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(speech)
        mp.setOnPreparedListener { prepared ->
            if (token != generation) return@setOnPreparedListener
            applyRoute(prepared)
            playCue(START_HZ, START_MS) {
                if (token != generation) return@playCue
                try {
                    prepared.start()
                    startClock(token)
                } catch (_: IllegalStateException) {
                    fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
                }
            }
        }
        mp.setOnCompletionListener {
            if (token != generation) return@setOnCompletionListener
            stopClock()
            try {
                mp.stop()
            } catch (_: IllegalStateException) {
            }
            playCue(END_HZ, END_MS) {
                if (token == generation) stop()
            }
        }
        mp.setOnErrorListener { _, _, _ ->
            if (token == generation) fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
            true
        }
        try {
            applyRoute(mp)
            mp.setDataSource(app, store.itemUri(id, sourcePackage))
            mp.prepareAsync()
        } catch (_: Exception) {
            fail(app.getString(com.brian.dialbuddy.R.string.play_failed))
        }
    }

    private fun requestDownload(id: Long, sourcePackage: String?): Boolean {
        val pkg = sourcePackage?.takeIf { it.isNotBlank() } ?: FI_PACKAGE
        val intent = Intent(VoicemailContract.ACTION_FETCH_VOICEMAIL).apply {
            setDataAndType(store.itemUri(id, pkg), VOICEMAIL_ITEM)
            setPackage(pkg)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        return try {
            app.sendBroadcast(intent)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun watch(id: Long, sourcePackage: String?, token: Int) {
        val obs = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                checkReady(id, sourcePackage, token)
            }
        }
        observer = obs
        app.contentResolver.registerContentObserver(VoicemailContract.Voicemails.CONTENT_URI, true, obs)
        val deadline = SystemClock.elapsedRealtime() + DOWNLOAD_TIMEOUT_MS
        val task = object : Runnable {
            override fun run() {
                if (token != generation || preparing) return
                checkReady(id, sourcePackage, token)
                if (token == generation && !preparing) {
                    if (SystemClock.elapsedRealtime() < deadline) {
                        main.postDelayed(this, POLL_MS)
                    } else {
                        fail(app.getString(com.brian.dialbuddy.R.string.download_failed))
                    }
                }
            }
        }
        poll = task
        main.postDelayed(task, POLL_MS)
    }

    private fun checkReady(id: Long, sourcePackage: String?, token: Int) {
        if (token != generation || preparing) return
        io.execute {
            val ready = try {
                store.media(id).hasContent
            } catch (_: Exception) {
                false
            }
            main.post {
                if (token != generation || preparing || !ready) return@post
                preparing = true
                startPlayer(id, sourcePackage, token)
            }
        }
    }

    private fun applyRoute(mp: MediaPlayer) {
        engageRoute()
        outputDevice()?.let { mp.setPreferredDevice(it) }
    }

    private fun engageRoute() {
        if (previousMode == null) previousMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        outputDevice()?.let { audio.setCommunicationDevice(it) }
    }

    private fun outputDevice(): AudioDeviceInfo? {
        val type = if (speakerOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        return audio.availableCommunicationDevices.firstOrNull { it.type == type }
            ?: audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == type }
    }

    private fun playCue(frequencyHz: Double, durationMs: Int, then: () -> Unit) {
        stopCue()
        engageRoute()
        val samples = cueSamples(frequencyHz, durationMs)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(cueAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(CUE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        } catch (_: IllegalArgumentException) {
            main.post(then)
            return
        }
        outputDevice()?.let { track.preferredDevice = it }
        track.write(samples, 0, samples.size)
        cueTrack = track
        try {
            track.play()
        } catch (_: IllegalStateException) {
            releaseCueTrack()
            main.post(then)
            return
        }
        val task = Runnable {
            cueTask = null
            releaseCueTrack()
            then()
        }
        cueTask = task
        main.postDelayed(task, durationMs.toLong() + CUE_GAP_MS)
    }

    private fun stopCue() {
        cueTask?.let { main.removeCallbacks(it) }
        cueTask = null
        releaseCueTrack()
    }

    private fun releaseCueTrack() {
        cueTrack?.let { track ->
            try {
                track.pause()
                track.flush()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }
        cueTrack = null
    }

    private fun cueSamples(frequencyHz: Double, durationMs: Int): ShortArray {
        val count = CUE_RATE * durationMs / 1000
        val samples = ShortArray(count)
        val edge = (CUE_RATE * 0.012).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val envelope = when {
                i < edge -> i.toFloat() / edge
                i > count - edge -> (count - i).toFloat() / edge
                else -> 1f
            }
            val wave = kotlin.math.sin(2.0 * Math.PI * frequencyHz * i / CUE_RATE)
            samples[i] = (wave * envelope * 0.55 * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun restoreRoute() {
        audio.clearCommunicationDevice()
        previousMode?.let { audio.mode = it }
        previousMode = null
    }

    private fun requestFocus(): Boolean {
        if (focusRequest != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(speech)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) main.post { stop() }
            }
            .build()
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return false
        focusRequest = request
        return true
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releasePlayer() {
        player?.let { mp ->
            mp.setOnPreparedListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            try {
                mp.reset()
            } catch (_: IllegalStateException) {
            }
            mp.release()
        }
        player = null
    }

    private fun startClock(token: Int) {
        stopClock()
        val task = object : Runnable {
            override fun run() {
                if (token != generation) return
                playedFraction(player)?.let(onProgress)
                if (token == generation) main.postDelayed(this, CLOCK_MS)
            }
        }
        clock = task
        main.post(task)
    }

    private fun stopClock() {
        clock?.let { main.removeCallbacks(it) }
        clock = null
    }

    private fun playedFraction(mp: MediaPlayer?): Float? {
        if (mp == null) return null
        return try {
            if (!mp.isPlaying) return null
            val duration = mp.duration
            if (duration <= 0) 0f else (mp.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun clearWatch() {
        poll?.let { main.removeCallbacks(it) }
        poll = null
        observer?.let { app.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun fail(message: String) {
        stop()
        onError(message)
    }

    private companion object {
        const val FI_PACKAGE = "com.google.android.apps.tycho"
        const val VOICEMAIL_ITEM = "vnd.android.cursor.item/voicemail"
        const val DOWNLOAD_TIMEOUT_MS = 25_000L
        const val POLL_MS = 1_000L
        const val CLOCK_MS = 100L
        const val CUE_RATE = 16000
        const val CUE_GAP_MS = 40L
        const val START_HZ = 880.0
        const val END_HZ = 440.0
        const val START_MS = 180
        const val END_MS = 280
    }
}
