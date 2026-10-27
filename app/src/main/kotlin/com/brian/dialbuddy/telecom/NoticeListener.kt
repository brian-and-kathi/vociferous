package com.brian.dialbuddy.telecom

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.brian.dialbuddy.data.NoticeLog

class NoticeListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        NoticeLog.attach(this)
    }

    override fun onListenerConnected() {
        connected = this
        NoticeLog.sync(this, activeNotifications?.toList().orEmpty())
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NoticeLog.posted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NoticeLog.dismissed(sbn.key, System.currentTimeMillis())
    }

    companion object {
        @Volatile
        private var connected: NoticeListener? = null

        fun cancel(key: String) {
            val service = connected ?: return
            try {
                service.cancelNotification(key)
            } catch (_: SecurityException) {
            }
        }

        fun resync() {
            val service = connected ?: return
            NoticeLog.sync(service, service.activeNotifications?.toList().orEmpty())
        }
    }
}
