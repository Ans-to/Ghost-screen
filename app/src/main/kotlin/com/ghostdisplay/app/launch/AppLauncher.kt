package com.ghostdisplay.app.launch

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.Display

/**
 * Launches a target app onto a virtual display using the three-step fallback
 * ladder required by the architecture:
 *
 *  1. Direct [ActivityOptions.setLaunchDisplayId].
 *  2. Launch normally, then [ActivityManager.moveTaskToDisplay] (hidden API,
 *     attempted via reflection; typically blocked for third-party apps).
 *  3. Report a clear per-app "not supported on this device" result. Never a
 *     silent failure.
 *
 * Verification of the resulting task's displayId is done by the caller using
 * Accessibility windows (this class cannot see other apps' task ids on
 * modern Android). [LaunchResult.Launched] therefore means "the system
 * accepted the startActivity call"; [verifiedOnDisplay] is filled in later.
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

    fun launch(
        packageName: String,
        activityName: String,
        virtualDisplayId: Int,
    ): LaunchResult {
        val errors = mutableListOf<String>()

        val step1 = launchOnDisplay(packageName, activityName, virtualDisplayId)
        if (step1.isSuccess) {
            val taskId = step1.getOrNull()
            return LaunchResult.Launched(
                step = 1,
                displayId = virtualDisplayId,
                taskId = taskId,
                notes = listOf(
                    "startActivity accepted with setLaunchDisplayId($virtualDisplayId).",
                    "Confirm with Accessibility that the task actually landed on the virtual display — some OEMs ignore the display id and still open on the phone screen.",
                ),
            )
        }
        errors += "Step 1 (setLaunchDisplayId): ${step1.exceptionOrNull()?.message}"

        val step2 = launchThenMove(packageName, activityName, virtualDisplayId, errors)
        if (step2 != null) return step2

        errors += "Step 3: this device/app combination cannot place the task on a virtual display from a non-privileged app."
        return LaunchResult.Failed(
            lastStep = 3,
            errors = errors,
            openedOnDefaultDisplay = true,
        )
    }

    private fun launchOnDisplay(
        packageName: String,
        activityName: String,
        displayId: Int,
    ): Result<Int?> {
        return runCatching {
            val intent = launchIntent(packageName, activityName)
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            applyCallerDisplayId(options, displayId)
            context.startActivity(intent, options.toBundle())
            peekTaskId(packageName)
        }
    }

    private fun launchThenMove(
        packageName: String,
        activityName: String,
        displayId: Int,
        errors: MutableList<String>,
    ): LaunchResult? {
        val launched = runCatching {
            context.startActivity(launchIntent(packageName, activityName))
        }
        if (launched.isFailure) {
            errors += "Step 2 launch: ${launched.exceptionOrNull()?.message}"
            return null
        }

        val taskId = peekTaskId(packageName)
        if (taskId == null) {
            errors += "Step 2: Android no longer exposes other apps' task ids to third-party apps, so moveTaskToDisplay cannot be aimed. " +
                "The app may have opened on the physical screen."
            return LaunchResult.Failed(
                lastStep = 2,
                errors = errors.toList() + "Could not obtain a task id to move.",
                openedOnDefaultDisplay = true,
            )
        }

        val moved = moveTaskToDisplay(taskId, displayId)
        return if (moved.isSuccess) {
            LaunchResult.Launched(
                step = 2,
                displayId = displayId,
                taskId = taskId,
                notes = listOf("Launched on default display then moved task $taskId → display $displayId."),
            )
        } else {
            errors += "Step 2 moveTaskToDisplay($taskId, $displayId): ${moved.exceptionOrNull()?.message}"
            null
        }
    }

    /**
     * Hidden API. Expected to throw / be blocked on production devices; the
     * failure is reported rather than swallowed.
     */
    fun moveTaskToDisplay(taskId: Int, displayId: Int): Result<Unit> {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method = am.javaClass.methods.firstOrNull { it.name == "moveTaskToDisplay" && it.parameterTypes.size == 2 }
                ?: ActivityManager::class.java.methods.firstOrNull { it.name == "moveTaskToDisplay" }
                ?: throw UnsupportedOperationException(
                    "moveTaskToDisplay is not available. It is a hidden system API and this device does not expose it to GhostDisplay.",
                )
            method.isAccessible = true
            method.invoke(am, taskId, displayId)
            Unit
        }
    }

    /**
     * Best-effort: ActivityManager.getAppTasks() only returns *this* app's
     * tasks on modern Android. We still try getRunningTasks (deprecated, also
     * filtered) in case an OEM is more permissive. Returning null is the
     * expected case and is not treated as a crash.
     */
    fun peekTaskId(packageName: String): Int? {
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

    private fun launchIntent(packageName: String, activityName: String): Intent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(packageName, activityName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT,
            )
        }
        return intent
    }

    private fun applyCallerDisplayId(options: ActivityOptions, displayId: Int) {
        // Hidden ActivityOptions.setCallerDisplayId(int) on some versions.
        runCatching {
            val m = ActivityOptions::class.java.methods.firstOrNull {
                it.name == "setCallerDisplayId" && it.parameterTypes.size == 1
            } ?: return
            m.invoke(options, displayId)
        }
    }

    companion object {
        private const val TAG = "GhostLauncher"
        const val DEFAULT_DISPLAY = Display.DEFAULT_DISPLAY
    }
}
