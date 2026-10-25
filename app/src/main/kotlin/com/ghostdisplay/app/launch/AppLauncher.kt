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

/**
 * Places a target app on a virtual display.
 *
 * HyperOS denies startActivity of a *foreign* package with launchDisplayId.
 * We only send launchDisplayId for our own [DisplayTrampolineActivity], and
 * only from a resumed Activity (not a display-bound Context — those inject
 * launchDisplayId with a null activity token and HyperOS then parks the
 * starter on the phone). The trampoline starts the target with no display id
 * after it has actually landed on the hidden display.
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

        val session = TrampolineSession(packageName, activityName, virtualDisplayId)
        TrampolineSession.current = session
        try {
            val trampoline = Intent(host, DisplayTrampolineActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            }

            val step1 = runCatching {
                host.startActivity(trampoline, optionsFor(virtualDisplayId).toBundle())
            }
            if (step1.isFailure) {
                errors += "Step 1 (own starter setLaunchDisplayId): ${step1.exceptionOrNull()?.message}"
            } else {
                when (val outcome = session.awaitFinished(FIRST_WAIT_MS)) {
                    is TrampolineSession.Outcome.Ok -> {
                        return launched(1, outcome.displayId, outcome.taskId, "starter landed on hidden display")
                    }
                    is TrampolineSession.Outcome.Err -> {
                        errors += "Step 1: ${outcome.message}"
                    }
                    null -> {
                        errors += "Step 1: starter did not confirm the hidden display (display ${session.lastDisplayId})"
                    }
                }
            }

            var taskId = session.taskId ?: session.awaitTaskId(TASK_WAIT_MS)
            if (taskId == null && step1.isFailure) {
                val behind = ActivityOptions.makeTaskLaunchBehind()
                val step2start = runCatching { host.startActivity(trampoline, behind.toBundle()) }
                if (step2start.isFailure) {
                    errors += "Step 2 (own starter launch-behind): ${step2start.exceptionOrNull()?.message}"
                } else {
                    taskId = session.awaitTaskId(TASK_WAIT_MS)
                }
            }

            if (taskId == null) {
                errors += "GhostDisplay starter never produced a task to move onto the hidden display. $packageName was not started. The app was not opened on the phone screen."
                return LaunchResult.Failed(2, errors, false)
            }

            val moved = HiddenTaskMover.moveTaskToDisplay(context, taskId, virtualDisplayId)
            if (moved.isFailure) {
                errors += "Step 2 (move own starter onto hidden display): ${moved.exceptionOrNull()?.message}"
                errors += "The hidden display exists, but this device will not place GhostDisplay's own starter activity on it, so $packageName was not started. The app was not opened on the phone screen."
                return LaunchResult.Failed(2, errors, false)
            }

            when (val outcome = session.awaitFinished(SECOND_WAIT_MS)) {
                is TrampolineSession.Outcome.Ok -> {
                    return launched(
                        2,
                        outcome.displayId,
                        outcome.taskId,
                        "reparented GhostDisplay starter via ${moved.getOrNull()}",
                    )
                }
                is TrampolineSession.Outcome.Err -> {
                    errors += "Step 2 after ${moved.getOrNull()}: ${outcome.message}"
                }
                null -> {
                    errors += "Step 2 after ${moved.getOrNull()}: starter still on display ${session.lastDisplayId}"
                }
            }

            errors += "The hidden display exists, but this device will not place GhostDisplay's own starter activity on it, so $packageName was not started. The app was not opened on the phone screen."
            return LaunchResult.Failed(2, errors, false)
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

    private fun optionsFor(displayId: Int): ActivityOptions {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        applyCallerDisplayId(options, displayId)
        applyFullscreenWindowing(options)
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

    private fun applyCallerDisplayId(options: ActivityOptions, displayId: Int) {
        runCatching {
            val m = ActivityOptions::class.java.methods.firstOrNull {
                it.name == "setCallerDisplayId" && it.parameterTypes.size == 1
            } ?: return
            m.invoke(options, displayId)
        }
    }

    private fun applyFullscreenWindowing(options: ActivityOptions) {
        runCatching {
            val m = ActivityOptions::class.java.methods.firstOrNull {
                it.name == "setLaunchWindowingMode" && it.parameterTypes.size == 1
            } ?: return
            m.invoke(options, WINDOWING_MODE_FULLSCREEN)
        }
    }

    companion object {
        private const val TAG = "GhostLauncher"
        const val DEFAULT_DISPLAY = Display.DEFAULT_DISPLAY
        private const val WINDOWING_MODE_FULLSCREEN = 1
        private const val FIRST_WAIT_MS = 2_500L
        private const val SECOND_WAIT_MS = 2_500L
        private const val TASK_WAIT_MS = 800L
    }
}
