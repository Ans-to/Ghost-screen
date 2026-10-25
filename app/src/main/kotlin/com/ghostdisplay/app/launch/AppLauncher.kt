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
 * Launches a target app onto a virtual display.
 *
 * The startActivity call MUST run on a resumed Activity. Starting it from
 * [com.ghostdisplay.app.service.GhostService] is the Permission Denial in
 * Active sessions (“starting Intent … from ProcessRecord”).
 *
 *  1. Explicit component + [ActivityOptions.setLaunchDisplayId] from the
 *     resumed GhostDisplay screen.
 *  2. Package launch intent, still with setLaunchDisplayId — never onto
 *     the phone screen.
 *  3. Report unsupported. Never a silent failure, never a leak onto display 0.
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

        val step1 = launchOnDisplay(explicitIntent(packageName, activityName), virtualDisplayId)
        if (step1.isSuccess) {
            return LaunchResult.Launched(
                step = 1,
                displayId = virtualDisplayId,
                taskId = step1.getOrNull(),
                notes = listOf(
                    "startActivity accepted with setLaunchDisplayId($virtualDisplayId) from the visible GhostDisplay screen.",
                ),
            )
        }
        errors += "Step 1 (setLaunchDisplayId): ${step1.exceptionOrNull()?.message}"

        val pkgIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (pkgIntent != null) {
            pkgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            val step2 = launchOnDisplay(pkgIntent, virtualDisplayId)
            if (step2.isSuccess) {
                return LaunchResult.Launched(
                    step = 2,
                    displayId = virtualDisplayId,
                    taskId = step2.getOrNull(),
                    notes = listOf(
                        "Package launch intent accepted with setLaunchDisplayId($virtualDisplayId).",
                    ),
                )
            }
            errors += "Step 2 (package launch intent on hidden display): ${step2.exceptionOrNull()?.message}"
        } else {
            errors += "Step 2: no launcher intent for $packageName."
        }

        errors += "Step 3: this device will not place the task on a virtual display from GhostDisplay. The app was not opened on the phone screen."
        return LaunchResult.Failed(
            lastStep = 3,
            errors = errors,
            openedOnDefaultDisplay = false,
        )
    }

    private fun launchOnDisplay(intent: Intent, displayId: Int): Result<Int?> {
        return runCatching {
            val host = ForegroundLaunchHost.requireResumed()
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            applyCallerDisplayId(options, displayId)
            host.startActivity(intent, options.toBundle())
            peekTaskId(intent.component?.packageName ?: intent.`package`.orEmpty())
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

    private fun explicitIntent(packageName: String, activityName: String): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(packageName, activityName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT,
            )
        }
    }

    private fun applyCallerDisplayId(options: ActivityOptions, displayId: Int) {
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
