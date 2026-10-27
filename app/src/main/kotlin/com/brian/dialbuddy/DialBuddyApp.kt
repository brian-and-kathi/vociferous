package com.brian.dialbuddy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.brian.dialbuddy.data.NoticeLog

class DialBuddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NoticeLog.attach(this)
        val notifications = getSystemService(NotificationManager::class.java)
        // The first channel was high importance, so Android drew it as a banner over the in-call screen.
        notifications.deleteNotificationChannel(LEGACY_CALL_CHANNEL_ID)
        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            getString(R.string.call_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.call_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        notifications.createNotificationChannel(channel)
    }

    companion object {
        const val CALL_CHANNEL_ID = "calls-status"
        private const val LEGACY_CALL_CHANNEL_ID = "calls"
    }
}
