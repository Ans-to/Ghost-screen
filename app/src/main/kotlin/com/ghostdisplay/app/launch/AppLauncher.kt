package com.ghostdisplay.app.launch

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.delay

/**
 * Places a target app on a virtual display using the AOSP multi-display
 * launch policy (Android 10+):
 *
 * https://source.android.com/docs/core/display/multi_display/activity-launch
 *
 * 1. An unprivileged app may launch **its own** activity onto a virtual
 *    display it created (`setLaunchDisplayId`). SafeActivityOptions only
 *    throws “with launchDisplayId: N” when that option is set **and**
 *    isCallerAllowedToLaunchOnDisplay is false.
 * 2. Other packages (Chrome included) cannot be started with
 *    launchDisplayId — they lack FLAG_ALLOW_EMBEDDED and we do not hold
 *    ACTIVITY_EMBEDDING. That is the 1.3.3 HyperOS denial.
 * 3. “By default, the activity launches on the same display as the caller.”
 *    After our [DisplayTrampolineActivity] is actually on the virtual
 *    display, it starts the target with a plain Activity.startActivity
 *    and **no** ActivityOptions, so the target inherits that display.
 *
 * Launch-behind options are not part of this policy and can keep the starter
 * from attaching to the virtual display.
 *
 * Never start the target on display 0.
 */
class AppLauncher(
    private val context: Context,
) {
    sealed class LaunchResult {
        data class Launched(
            val step: Int,
            val displayId: Int,
            val taskId: Int?,
            val notes: List<String>,
        ) : LaunchResult()

        data class Failed(
            val lastStep: Int,
            val errors: List<String>,
            val openedOnDefaultDisplay: Boolean,
        ) : LaunchResult()
    }

    private var occupancy: Presentation? = null

    suspend fun launch(
        packageName: String,
        activityName: String,
        virtualDisplayId: Int,
    ): LaunchResult {
        val errors = mutableListOf<String>()
        val host = try {
            ForegroundLaunchHost.requireResumed()
        } catch (t: Throwable) {
            return LaunchResult.Failed(0, listOf(t.message ?: "No visible GhostDisplay screen."), false)
        }
        val display = displayOf(host, virtualDisplayId)
            ?: return LaunchResult.Failed(0, listOf("Virtual display $virtualDisplayId is gone."), false)

        occupy(host, display)
        delay(200)

        val session = TrampolineSession(packageName, activityName, virtualDisplayId)
        TrampolineSession.current = session
        try {
            val trampoline = Intent(host, DisplayTrampolineActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }

            val allowed = isStartAllowed(host, virtualDisplayId, trampoline)
            if (allowed == false) {
                errors += "Android reports GhostDisplay cannot start its own activity on virtual display $virtualDisplayId (ActivityManager.isActivityStartAllowedOnDisplay=false). " +
                    "This device blocks placing activities on an app-created virtual display."
                return LaunchResult.Failed(1, errors, false)
            }

            val step1 = runCatching {
                host.startActivity(trampoline, optionsForOwnActivity(virtualDisplayId).toBundle())
            }
            if (step1.isFailure) {
                errors += "Own starter on hidden display: ${step1.exceptionOrNull()?.message}"
                return LaunchResult.Failed(1, errors, false)
            }

            when (val outcome = session.awaitFinished(WAIT_MS)) {
                is TrampolineSession.Outcome.Ok -> {
                    return launched(1, outcome.displayId, outcome.taskId, "own starter on hidden display, target inherited it")
                }
                is TrampolineSession.Outcome.Err -> {
                    errors += outcome.message
                    return LaunchResult.Failed(1, errors, false)
                }
                null -> {
                    errors += "Own starter did not land on virtual display $virtualDisplayId (still display ${session.lastDisplayId}). $packageName was not started."
                    return LaunchResult.Failed(1, errors, false)
                }
            }
        } finally {
            if (TrampolineSession.current === session) {
                TrampolineSession.current = null
            }
            releaseOccupancy()
        }
    }

    private fun launched(step: Int, displayId: Int, taskId: Int?, how: String) = LaunchResult.Launched(
        step = step,
        displayId = displayId,
        taskId = taskId,
        notes = listOf("Hidden starter confirmed ($how) on display $displayId."),
    )

    private fun occupy(host: Activity, display: Display) {
        try {
            occupancy?.dismiss()
            val presentation = OccupancyPresentation(host, display)
            presentation.show()
            occupancy = presentation
        } catch (t: Throwable) {
            Log.i(TAG, "Occupancy presentation not shown: ${t.message}")
            occupancy = null
        }
    }

    private fun releaseOccupancy() {
        val shown = occupancy
        occupancy = null
        if (shown == null) return
        try {
            shown.dismiss()
        } catch (t: Throwable) {
            Log.i(TAG, "Occupancy presentation dismiss: ${t.message}")
        }
    }

    private fun displayOf(host: Activity, displayId: Int): Display? {
        val dm = host.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplay(displayId)
    }

    private fun isStartAllowed(host: Activity, displayId: Int, intent: Intent): Boolean? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val am = host.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.isActivityStartAllowedOnDisplay(host, displayId, intent)
        } catch (t: Throwable) {
            Log.i(TAG, "isActivityStartAllowedOnDisplay: ${t.message}")
            null
        }
    }

    private fun optionsForOwnActivity(displayId: Int): ActivityOptions {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        return options
    }

    fun moveTaskToDisplay(taskId: Int, displayId: Int): Result<Unit> {
        return HiddenTaskMover.moveTaskToDisplay(context, taskId, displayId).map { }
    }

    fun peekTaskId(packageName: String): Int? {
        if (packageName.isBlank()) return null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            @Suppress("DEPRECATION")
            val tasks = am.getRunningTasks(16)
            val match = tasks.firstOrNull { it.topActivity?.packageName == packageName }
            if (match != null) return match.id
        } catch (t: Throwable) {
            Log.d(TAG, "getRunningTasks unavailable: ${t.message}")
        }
        try {
            val our = am.appTasks
            for (task in our) {
                val info = task.taskInfo
                val top = if (Build.VERSION.SDK_INT >= 23) info.topActivity else null
                if (top?.packageName == packageName) return info.id
            }
        } catch (t: Throwable) {
            Log.d(TAG, "getAppTasks failed: ${t.message}")
        }
        return null
    }

    companion object {
        private const val TAG = "GhostLauncher"
        const val DEFAULT_DISPLAY = Display.DEFAULT_DISPLAY
        private const val WAIT_MS = 4_000L
    }
}
