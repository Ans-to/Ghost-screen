package com.ghostdisplay.app.launch

import android.content.Context
import android.content.Intent
import com.ghostdisplay.app.control.RemoteControlActivity
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.display.DisplayConfig
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.service.GhostStopReceiver
import com.ghostdisplay.app.service.SessionRepository
import com.ghostdisplay.app.session.AppViewerActivity

/** Shared start / stop / viewer entry points. No per-app branches. */
object HiddenLaunch {

    fun needsMediaProjection(profile: AppProfile?): Boolean {
        return profile?.surfaceModeEnum() == DisplayConfig.SurfaceMode.MEDIA_PROJECTION ||
            profile?.secure == true ||
            profile?.audioEnabled != false
    }

    fun hardNeedsMediaProjection(profile: AppProfile?): Boolean {
        return profile?.surfaceModeEnum() == DisplayConfig.SurfaceMode.MEDIA_PROJECTION ||
            profile?.secure == true
    }

    fun liveFor(packageName: String): GhostSession? =
        SessionRepository.all().firstOrNull {
            it.packageName == packageName && it.status == SessionStatus.RUNNING
        }

    fun start(
        context: Context,
        app: LaunchableApp,
        projectionResultCode: Int? = null,
        projectionData: Intent? = null,
        additionalWindow: Boolean = false,
        financeConfirmed: Boolean = false,
    ) {
        GhostService.startHidden(
            context,
            app.packageName,
            app.activityName,
            app.label,
            projectionResultCode,
            projectionData,
            additionalWindow = additionalWindow,
            financeConfirmed = financeConfirmed,
        )
    }

    fun stop(context: Context, session: GhostSession) {
        try {
            val i = Intent(context, GhostService::class.java)
                .setAction(GhostStopReceiver.ACTION_STOP_SESSION)
                .putExtra(GhostService.EXTRA_SESSION_ID, session.id)
            context.startService(i)
        } catch (_: Throwable) {
        }
    }

    fun stopAll(context: Context) {
        GhostService.stopAll(context)
    }

    fun openViewer(context: Context, session: GhostSession) {
        try {
            context.startActivity(
                Intent(context, AppViewerActivity::class.java)
                    .putExtra(AppViewerActivity.EXTRA_SESSION_ID, session.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
        }
    }

    fun openRemote(context: Context, session: GhostSession) {
        try {
            context.startActivity(
                Intent(context, RemoteControlActivity::class.java)
                    .putExtra(RemoteControlActivity.EXTRA_SESSION_ID, session.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Throwable) {
        }
    }
}
