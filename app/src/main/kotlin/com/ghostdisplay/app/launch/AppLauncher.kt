package com.ghostdisplay.app.launch

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager

/**
 * Places a target app on a virtual display.
 *
 * Starting a foreign app from GhostService is a Permission Denial (the
 * ProcessRecord error on Active sessions). Starting it from a display-0
 * activity with only setLaunchDisplayId is also blocked on several OEMs.
 *
 * Order:
 *  1. [Context.createDisplayContext] / [Context.createWindowContext] for the
 *     virtual display, then startActivity — the Context itself is bound to
 *     that display.
 *  2. Resumed activity + [ActivityOptions.setLaunchDisplayId].
 *  3. Our [DisplayTrampolineActivity] on the virtual display starts the
 *     target from a caller that already lives there.
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

    fun launch(
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
            ?: return LaunchResult.Failed(
                0,
                listOf("Virtual display $virtualDisplayId is gone."),
                false,
            )

        val explicit = explicitIntent(packageName, activityName)

        val step1 = runCatching {
            startFromDisplayContext(host, display, virtualDisplayId, explicit)
            peekTaskId(packageName)
        }
        if (step1.isSuccess) {
            return launched(1, virtualDisplayId, step1.getOrNull(), "display context")
        }
        errors += "Step 1 (display context): ${step1.exceptionOrNull()?.message}"

        val step2 = runCatching {
            startWithLaunchDisplayId(host, virtualDisplayId, explicit)
            peekTaskId(packageName)
        }
        if (step2.isSuccess) {
            return launched(2, virtualDisplayId, step2.getOrNull(), "setLaunchDisplayId")
        }
        errors += "Step 2 (setLaunchDisplayId): ${step2.exceptionOrNull()?.message}"

        val step3 = runCatching {
            val trampoline = Intent(host, DisplayTrampolineActivity::class.java).apply {
                putExtra(DisplayTrampolineActivity.EXTRA_PACKAGE, packageName)
                putExtra(DisplayTrampolineActivity.EXTRA_ACTIVITY, activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startFromDisplayContext(host, display, virtualDisplayId, trampoline)
            peekTaskId(packageName)
        }
        if (step3.isSuccess) {
            return launched(3, virtualDisplayId, step3.getOrNull(), "trampoline on hidden display")
        }
        errors += "Step 3 (trampoline): ${step3.exceptionOrNull()?.message}"

        errors += "The hidden display exists, but this device will not place $packageName on it. The app was not opened on the phone screen."
        return LaunchResult.Failed(3, errors, false)
    }

    private fun launched(step: Int, displayId: Int, taskId: Int?, how: String) = LaunchResult.Launched(
        step = step,
        displayId = displayId,
        taskId = taskId,
        notes = listOf("startActivity accepted ($how) on display $displayId."),
    )

    private fun displayOf(host: Activity, displayId: Int): Display? {
        val dm = host.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplay(displayId)
    }

    private fun startFromDisplayContext(
        host: Activity,
        display: Display,
        displayId: Int,
        intent: Intent,
    ) {
        val bundle = optionsFor(displayId).toBundle()
        var last: Throwable? = null
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                host.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION, null)
                    .startActivity(intent, bundle)
                return
            } catch (t: Throwable) {
                last = t
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                host.createDisplayContext(display).startActivity(intent, bundle)
                return
            } catch (t: Throwable) {
                last = t
            }
        }
        try {
            startWithLaunchDisplayId(host, displayId, intent)
            return
        } catch (t: Throwable) {
            last = t
        }
        throw last ?: IllegalStateException("No display-bound start path on API ${Build.VERSION.SDK_INT}")
    }

    private fun startWithLaunchDisplayId(host: Activity, displayId: Int, intent: Intent) {
        host.startActivity(intent, optionsFor(displayId).toBundle())
    }

    private fun optionsFor(displayId: Int): ActivityOptions {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        applyCallerDisplayId(options, displayId)
        applyFullscreenWindowing(options)
        return options
    }

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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT
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
    }
}
