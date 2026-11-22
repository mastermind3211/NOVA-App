package com.nova.assistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NovaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: NovaNotificationListener? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    fun getNotificationSummaries(limit: Int = 5): List<String> {
        val active: Array<StatusBarNotification> = runCatching { activeNotifications }.getOrNull() ?: return emptyList()
        return active
            .filter { !it.isOngoing }
            .take(limit)
            .map { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                val app = sbn.packageName.substringAfterLast('.')
                "$app: $title - $text".trim()
            }
    }
}
