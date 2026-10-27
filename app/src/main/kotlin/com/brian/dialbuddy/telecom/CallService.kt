package com.brian.dialbuddy.telecom

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.brian.dialbuddy.DialBuddyApp
import com.brian.dialbuddy.InCallActivity
import com.brian.dialbuddy.R

class CallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        Calls.service = this
    }

    override fun onDestroy() {
        if (Calls.service === this) {
            UnknownRinger.stop()
            Calls.service = null
        }
        cancelNotification()
        super.onDestroy()
    }

    override fun onSilenceRinger() {
        UnknownRinger.silence()
    }

    override fun onCallAdded(call: Call) {
        Calls.add(call)
        val open = inCallIntent()
        startActivity(open)
        showNotificationText(call.details.handle?.schemeSpecificPart)
    }

    override fun onCallRemoved(call: Call) {
        Calls.remove(call)
        val remaining = Calls.state.value.calls.firstOrNull()
        if (remaining == null) {
            cancelNotification()
        } else {
            showNotificationText(remaining.number)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        Calls.updateAudio(audioState)
    }

    private fun inCallIntent(): Intent =
        Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun showNotificationText(number: String?) {
        val open = PendingIntent.getActivity(
            this,
            0,
            inCallIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = number?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_caller)
        val notification = NotificationCompat.Builder(this, DialBuddyApp.CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(getString(R.string.incoming_call_title))
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, false)
            .build()
        getSystemService(NotificationManager::class.java).notify(CALL_NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(CALL_NOTIFICATION_ID)
    }

    companion object {
        const val CALL_NOTIFICATION_ID = 1001
    }
}
