package com.ghostdisplay.app.indicator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ghostdisplay.app.R
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.session.AppViewerActivity

/**
 * Silent, minimized return-indicator notifications. One group, IMPORTANCE_MIN,
 * no sound, no vibration, no heads-up. Summary + per-session rows with Stop.
 */
object ReturnNotifications {
    const val CHANNEL_RETURN = "ghost_return"
    const val GROUP = "ghost_return_group"
    const val ID_SUMMARY = 2000
    const val ID_BASE = 2100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RETURN,
                "Hidden window return",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Silent indicator of apps running as hidden windows. No sound, no heads-up."
                setShowBadge(true)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    fun publish(context: Context, sessions: List<GhostSession>) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        val live = sessions.filter { it.status == SessionStatus.RUNNING }
        if (live.isEmpty()) {
            nm.cancel(ID_SUMMARY)
            (0 until 32).forEach { nm.cancel(ID_BASE + it) }
            return
        }
        live.forEachIndexed { index, session ->
            runCatching { nm.notify(ID_BASE + index, perSession(context, session, index)) }
        }
        runCatching { nm.notify(ID_SUMMARY, summary(context, live)) }
        // Cancel leftover ids if session count shrank.
        for (i in live.size until 32) nm.cancel(ID_BASE + i)
    }

    fun clear(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ID_SUMMARY)
        (0 until 32).forEach { nm.cancel(ID_BASE + it) }
    }

    private fun perSession(context: Context, session: GhostSession, index: Int): Notification {
        val open = PendingIntent.getActivity(
            context,
            100 + index,
            Intent(context, AppViewerActivity::class.java)
                .putExtra(AppViewerActivity.EXTRA_SESSION_ID, session.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getForegroundService(
            context,
            200 + index,
            Intent(context, GhostService::class.java)
                .setAction(GhostService.ACTION_STOP_SESSION_DIRECT)
                .putExtra(GhostService.EXTRA_SESSION_ID, session.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RETURN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${session.appLabel} · Running hidden")
            .setContentText("${session.tier} · same real app, hidden window")
            .setGroup(GROUP)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun summary(context: Context, live: List<GhostSession>): Notification {
        val names = live.joinToString { it.appLabel }
        val inbox = NotificationCompat.InboxStyle()
            .setSummaryText("${live.size} hidden windows")
        live.forEach { inbox.addLine("${it.appLabel} · ${it.tier}") }
        val open = PendingIntent.getActivity(
            context,
            99,
            Intent(context, com.ghostdisplay.app.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RETURN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("GhostDisplay")
            .setContentText("$names running hidden")
            .setStyle(inbox)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
