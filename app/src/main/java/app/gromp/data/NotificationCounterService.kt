package app.gromp.data

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.gromp.GrompApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Counts notifications per app per day. Only the package name and a count are kept; content is never read. */
class NotificationCounterService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing || sbn.packageName == packageName) return
        val flags = sbn.notification?.flags ?: 0
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val repo = (application as GrompApp).container.repository
        scope.launch { runCatching { repo.countNotification(sbn.packageName) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
