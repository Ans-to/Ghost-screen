package com.ghostdisplay.app.safety

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional filter: when a hidden session has "block notifications" on, cancel
 * that package's status-bar notifications. Does not prevent the app from
 * *generating* them — it only drops them from the shade.
 */
class GhostNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        _connected.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val blocked = filteredPackages
        if (n.packageName in blocked) {
            runCatching { cancelNotification(n.key) }
        }
    }

    companion object {
        @Volatile
        var instance: GhostNotificationListenerService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        @Volatile
        var filteredPackages: Set<String> = emptySet()

        fun isConnected(): Boolean = instance != null
    }
}
