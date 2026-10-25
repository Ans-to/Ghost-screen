package com.ghostdisplay.app.launch

import android.app.Activity

/**
 * Android 10+ refuses [Activity.startActivity] from a Service, even a
 * foreground one. `setLaunchDisplayId` is therefore impossible unless a
 * GhostDisplay activity is resumed. HoldActionActivity and MainActivity
 * register here; [AppLauncher] starts the target from that activity.
 */
object ForegroundLaunchHost {

    @Volatile
    private var resumed: Activity? = null

    fun onResume(activity: Activity) {
        resumed = activity
    }

    fun onPause(activity: Activity) {
        if (resumed === activity) resumed = null
    }

    fun requireResumed(): Activity {
        val activity = resumed
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            throw IllegalStateException(
                "Android blocked starting another app because GhostDisplay was not on screen. " +
                    "A background service is not allowed to open Chrome (or any app) onto the hidden display. " +
                    "Keep GhostDisplay open until the hidden window starts.",
            )
        }
        return activity
    }
}
