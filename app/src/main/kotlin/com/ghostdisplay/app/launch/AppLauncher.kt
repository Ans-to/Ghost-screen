package com.ghostdisplay.app.launch

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * SafeActivityOptions throws “with launchDisplayId: N” **only** when that
 * option is set and isCallerAllowedToLaunchOnDisplay is false. HyperOS
 * 1.3.8 returned isActivityStartAllowedOnDisplay=false when the source
 * context was the phone activity. AOSP keys that check on the source
 * context: a Presentation already shown on the virtual display is
 * associated with it; the phone activity is not.
 *
 * When launchDisplayId is refused, AOSP still allows:
 *  1. Starting **without** that option (the launchDisplayId gate is skipped).
 *  2. Reparenting a task we already own onto the display.
 * The target is never started until the starter’s displayId is the hidden
 * one, so it inherits that display and is never opened on display 0.
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
        val notes = mutableListOf<String>()
        val host = try {
            ForegroundLaunchHost.requireResumed()
        } catch (t: Throwable) {
            return LaunchResult.Failed(0, listOf(t.message ?: "No visible GhostDisplay screen."), false)
        }
        val display = displayOf(host, virtualDisplayId)
            ?: return LaunchResult.Failed(0, listOf("Virtual display $virtualDisplayId is gone."), false)

        occupy(host, display)
        delay(300)

        val session = TrampolineSession(packageName, activityName, virtualDisplayId)
        TrampolineSession.current = session
        val trampoline = Intent(host, DisplayTrampolineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        val displayContext = occupancy?.context
        try {
            val fromDisplay = isStartAllowed(displayContext ?: host, virtualDisplayId, trampoline)
            val fromPhone = isStartAllowed(host, virtualDisplayId, trampoline)
            notes += "isActivityStartAllowedOnDisplay display-context=$fromDisplay phone-context=$fromPhone " +
                "flags=${displayFlags(display)} secondary=${hasSecondaryActivityFeature(host)}"

            var starterRunning = false
            if (fromDisplay != false && displayContext != null) {
                val placed = startOwn(
                    displayContext,
                    trampoline,
                    optionsForOwnActivity(virtualDisplayId),
                    "display-context+launchDisplayId",
                )
                if (placed != null) {
                    errors += placed
                } else {
                    starterRunning = true
                    when (val outcome = session.awaitFinished(LAND_MS)) {
                        is TrampolineSession.Outcome.Ok -> {
                            return launched(1, outcome.displayId, outcome.taskId, notes, "display-context + launchDisplayId")
                        }
                        is TrampolineSession.Outcome.Err -> errors += outcome.message
                        null -> errors += "Starter did not land on display $virtualDisplayId after display-context launch (display ${session.lastDisplayId})."
                    }
                }
            } else {
                notes += "Skipped launchDisplayId from the phone activity (isActivityStartAllowedOnDisplay=$fromPhone)."
            }

            if (!starterRunning && !session.alreadyFinished()) {
                val placed = runCatching { host.startActivity(trampoline) }
                if (placed.isFailure) {
                    errors += "Own starter (phone activity, no launchDisplayId): ${placed.exceptionOrNull()?.message}"
                    return LaunchResult.Failed(1, errors + notes, false)
                }
                starterRunning = true
            }

            if (!session.alreadyFinished() && starterRunning) {
                val taskId = session.awaitTaskId(TASK_WAIT_MS)
                if (taskId == null) {
                    errors += "GhostDisplay starter did not create a task, so it cannot be moved onto the hidden display."
                    return LaunchResult.Failed(1, errors + notes, false)
                }
                val moved = HiddenTaskMover.moveTaskToDisplay(host, taskId, virtualDisplayId)
                if (moved.isSuccess) {
                    notes += "Reparented our starter via ${moved.getOrNull()}."
                } else {
                    errors += "Could not reparent our starter onto display $virtualDisplayId: ${moved.exceptionOrNull()?.message}"
                }
                when (val outcome = session.awaitFinished(MOVE_WAIT_MS)) {
                    is TrampolineSession.Outcome.Ok -> {
                        return launched(2, outcome.displayId, outcome.taskId, notes, "reparented our starter, target inherited it")
                    }
                    is TrampolineSession.Outcome.Err -> errors += outcome.message
                    null -> errors += "Own starter stayed on display ${session.lastDisplayId} after reparent (wanted $virtualDisplayId). $packageName was not started."
                }
            }

            return LaunchResult.Failed(2, errors + notes, false)
        } finally {
            if (TrampolineSession.current === session) {
                TrampolineSession.current = null
            }
            releaseOccupancy()
        }
    }

    private fun launched(
        step: Int,
        displayId: Int,
        taskId: Int?,
        notes: List<String>,
        how: String,
    ) = LaunchResult.Launched(
        step = step,
        displayId = displayId,
        taskId = taskId,
        notes = listOf("Hidden starter confirmed ($how) on display $displayId.") + notes,
    )

    private fun startOwn(
        from: Context,
        trampoline: Intent,
        options: ActivityOptions?,
        how: String,
    ): String? {
        val result = runCatching {
            if (options != null) {
                from.startActivity(trampoline, options.toBundle())
            } else {
                from.startActivity(trampoline)
            }
        }
        return if (result.isFailure) {
            "Own starter ($how): ${result.exceptionOrNull()?.message}"
        } else {
            null
        }
    }

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

    private fun displayFlags(display: Display): String {
        val flags = display.flags
        val names = buildList {
            if (flags and Display.FLAG_PRIVATE != 0) add("PRIVATE")
            if (flags and Display.FLAG_PRESENTATION != 0) add("PRESENTATION")
            if (flags and Display.FLAG_SECURE != 0) add("SECURE")
            add("raw=0x${Integer.toHexString(flags)}")
        }
        return names.joinToString("|")
    }

    private fun hasSecondaryActivityFeature(host: Activity): Boolean {
        return host.packageManager.hasSystemFeature(
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS,
        )
    }

    private fun isStartAllowed(from: Context, displayId: Int, intent: Intent): Boolean? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val am = from.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.isActivityStartAllowedOnDisplay(from, displayId, intent)
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
            Log.d(TAG, "getTaskId failed: ${t.message}")
        }
        return null
    }

    companion object {
        private const val TAG = "GhostLauncher"
        const val DEFAULT_DISPLAY = Display.DEFAULT_DISPLAY
        private const val LAND_MS = 2_500L
        private const val TASK_WAIT_MS = 2_000L
        private const val MOVE_WAIT_MS = 5_000L
    }
}
