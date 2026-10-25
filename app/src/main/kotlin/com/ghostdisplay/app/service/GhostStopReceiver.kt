package com.ghostdisplay.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GhostStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val stop = Intent(context, GhostService::class.java).setAction(action)
        intent.getStringExtra(GhostService.EXTRA_SESSION_ID)?.let {
            stop.putExtra(GhostService.EXTRA_SESSION_ID, it)
        }
        context.startService(stop)
    }

    companion object {
        const val ACTION_STOP_ALL = "com.ghostdisplay.app.action.STOP_ALL"
        const val ACTION_STOP_SESSION = "com.ghostdisplay.app.action.STOP_SESSION"
    }
}
