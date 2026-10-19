package com.brian.dialbuddy.telecom

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.Call
import androidx.core.net.toUri
import com.brian.dialbuddy.R
import com.brian.dialbuddy.domain.Interruption
import com.brian.dialbuddy.domain.RingPlayback
import com.brian.dialbuddy.domain.RingerMode
import com.brian.dialbuddy.domain.ringPlayback

object UnknownRinger {
    private val armed = LinkedHashSet<String>()
    private val seen = LinkedHashSet<String>()
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var playing = false
    private var silenced = false

    fun expect(number: String) {
        armed += key(number)
    }

    fun syncAttached() {
        val service = Calls.service ?: return
        sync(service, Calls.state.value.calls)
    }

    fun forget(number: String) {
        val key = key(number)
        armed.remove(key)
        seen.remove(key)
    }

    fun sync(context: Context, calls: List<CallLine>) {
        val present = calls.map { key(it.number) }.toSet()
        for (key in armed) {
            if (key in present) seen += key
        }
        val gone = seen.filter { it !in present }
        armed.removeAll(gone.toSet())
        seen.removeAll(gone.toSet())

        val ringing = calls.any { isRinging(it.state) && key(it.number) in armed }
        if (!ringing) {
            silenced = false
            stopPlayback()
            return
        }
        if (silenced) {
            stopPlayback()
            return
        }
        start(context)
    }

    fun silence() {
        silenced = true
        stopPlayback()
    }

    fun stop() {
        silenced = false
        armed.clear()
        seen.clear()
        stopPlayback()
    }

    private fun start(context: Context) {
        if (playing) return
        val playback = cue(context)
        if (!playback.sound && !playback.vibrate) return
        if (playback.sound) startTone(context)
        if (playback.vibrate) startVibration(context)
        playing = ringtone != null || playback.vibrate
    }

    private fun startTone(context: Context) {
        val uri = "android.resource://${context.packageName}/${R.raw.unknown_ring}".toUri()
        val tone = RingtoneManager.getRingtone(context, uri) ?: return
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        tone.isLooping = true
        tone.play()
        ringtone = tone
    }

    private fun startVibration(context: Context) {
        val effect = VibrationEffect.createWaveform(longArrayOf(400, 200, 400, 2000), 0)
        val motor = context.getSystemService(VibratorManager::class.java).defaultVibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            motor.vibrate(
                effect,
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_RINGTONE)
                    .build(),
            )
        } else {
            motor.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build(),
            )
        }
        vibrator = motor
    }

    private fun stopPlayback() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        playing = false
    }

    private fun cue(context: Context): RingPlayback {
        val audio = context.getSystemService(AudioManager::class.java)
        val mode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.Silent
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.Vibrate
            else -> RingerMode.Normal
        }
        val notifications = context.getSystemService(NotificationManager::class.java)
        val interruption = when (notifications.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> Interruption.All
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> Interruption.Priority
            else -> Interruption.Other
        }
        val vibrateWhenRinging = vibrateWhenRinging(context)
        return ringPlayback(
            mode = mode,
            interruption = interruption,
            priorityAllowsAnyone = interruption == Interruption.Priority && priorityAllowsAnyone(notifications),
            vibrateWhenRinging = vibrateWhenRinging,
        )
    }

    private fun vibrateWhenRinging(context: Context): Boolean {
        val intensity = Settings.System.getInt(context.contentResolver, "ring_vibration_intensity", -1)
        if (intensity >= 0) return intensity > 0
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.VIBRATE_WHEN_RINGING,
            0,
        ) == 1
    }

    private fun priorityAllowsAnyone(notifications: NotificationManager): Boolean {
        val policy = try {
            notifications.notificationPolicy
        } catch (_: SecurityException) {
            return false
        } ?: return false
        val allowsCalls = (policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS) != 0
        return allowsCalls && policy.priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY
    }

    private fun isRinging(state: Int): Boolean =
        state == Call.STATE_RINGING || state == Call.STATE_SIMULATED_RINGING

    private fun key(number: String): String = number.trim()
}
