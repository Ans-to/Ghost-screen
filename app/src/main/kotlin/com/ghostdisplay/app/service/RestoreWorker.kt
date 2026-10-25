package com.ghostdisplay.app.service

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ghostdisplay.app.GhostApp
import kotlinx.coroutines.flow.first

/**
 * After reboot / process death we NEVER auto-start a virtual display:
 * MediaProjection consent is interactive. We offer a notification instead.
 */
class RestoreWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? GhostApp ?: return Result.success()
        val restore = app.container.prefs.restoreOnBoot.first()
        if (!restore) return Result.success()
        val tabs = app.container.tabs.openTabs()
        if (tabs.isNotEmpty()) {
            GhostService.startBrowser(applicationContext)
        }
        val active = app.container.db.sessions().active()
        if (active.isEmpty() && tabs.isEmpty()) return Result.success()
        if (active.isNotEmpty()) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            GhostNotifications.ensureChannels(applicationContext)
            nm.notify(
                GhostNotifications.ID_RESTORE,
                GhostNotifications.restoreNotification(applicationContext, active.map { it.appLabel }),
            )
        }
        return Result.success()
    }
}
