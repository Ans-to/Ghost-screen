package com.ghostdisplay.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ghostdisplay.app.MainActivity
import com.ghostdisplay.app.R
import com.ghostdisplay.app.browser.ViewerActivity
import com.ghostdisplay.app.control.RemoteControlActivity

object GhostNotifications {
    const val CHANNEL_SESSION = "ghost_session"
    const val CHANNEL_RESTORE = "ghost_restore"
    const val ID_FOREGROUND = 1001
    const val ID_RESTORE = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SESSION,
                context.getString(R.string.channel_session_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_session_desc)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESTORE,
                context.getString(R.string.channel_restore_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun sessionNotification(context: Context, appLabel: String, sessionId: String?): Notification {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getForegroundService(
            context,
            1,
            Intent(context, GhostService::class.java).setAction(GhostService.ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val isBrowser = appLabel.contains("Persistent Browser")
        val remoteIntent = if (isBrowser) {
            Intent(context, ViewerActivity::class.java)
        } else {
            Intent(context, RemoteControlActivity::class.java).apply {
                if (sessionId != null) putExtra(RemoteControlActivity.EXTRA_SESSION_ID, sessionId)
            }
        }
        val remote = PendingIntent.getActivity(
            context,
            2,
            remoteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_running, appLabel))
            .setContentText(
                if (isBrowser) "Tabs keep running hidden. Viewer does not pause them. Stop from here at any time."
                else "Nothing is shown on your screen. Stop from here at any time.",
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (isBrowser) "Viewer" else context.getString(R.string.notification_remote), remote)
            .addAction(0, context.getString(R.string.notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun restoreNotification(context: Context, labels: List<String>): Notification {
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_RESTORE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (labels.isEmpty()) {
            "A hidden session was running. Tap to restore — you will need to grant screen-capture again if it used MediaProjection."
        } else {
            "Restore ${labels.joinToString()}? Screen-capture consent cannot survive a reboot; you will be asked again if needed."
        }
        return NotificationCompat.Builder(context, CHANNEL_RESTORE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Restore hidden session")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }
}
