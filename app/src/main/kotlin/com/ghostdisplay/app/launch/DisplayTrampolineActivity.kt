package com.ghostdisplay.app.launch

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display

/**
 * GhostDisplay activity that must already be on the virtual display before
 * it starts the target.
 *
 * HyperOS rejects startActivity of *another* package with launchDisplayId.
 * This activity starts the target with a plain startActivity so the target
 * inherits this window's display. If we are still on the phone screen we
 * wait until this window is moved and never start the target there.
 */
class DisplayTrampolineActivity : Activity() {

    private var startedTarget = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            maybeStart("onDisplayChanged:$displayId")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager().registerDisplayListener(displayListener, mainHandler)
        maybeStart("onCreate")
        window.decorView.post { maybeStart("post") }
        pollUntilStarted()
        window.decorView.postDelayed({
            if (!startedTarget && !isFinishing) {
                val session = TrampolineSession.current
                if (session != null && !session.alreadyFinished()) {
                    session.fail(
                        "GhostDisplay starter stayed on the phone screen (display ${displayId()}). " +
                            "The app was not opened.",
                    )
                }
                finish()
            }
        }, SELF_TIMEOUT_MS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        maybeStart("onAttachedToWindow")
    }

    override fun onResume() {
        super.onResume()
        maybeStart("onResume")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        maybeStart("onConfigurationChanged")
    }

    override fun onDestroy() {
        displayManager().unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    private fun displayManager(): DisplayManager =
        getSystemService(DISPLAY_SERVICE) as DisplayManager

    private fun pollUntilStarted() {
        maybeStart("poll")
        if (!startedTarget && !isFinishing) {
            mainHandler.postDelayed({ pollUntilStarted() }, POLL_MS)
        }
    }

    private fun displayId(): Int {
        val fromDecor = window?.decorView?.display?.displayId
        val fromActivity = display?.displayId
        return when {
            fromDecor != null && fromDecor != Display.DEFAULT_DISPLAY -> fromDecor
            fromActivity != null && fromActivity != Display.DEFAULT_DISPLAY -> fromActivity
            fromDecor != null -> fromDecor
            fromActivity != null -> fromActivity
            else -> Display.DEFAULT_DISPLAY
        }
    }

    private fun maybeStart(where: String) {
        val session = TrampolineSession.current
        if (session == null) {
            if (!isFinishing) finish()
            return
        }
        val id = displayId()
        session.noteLanded(id, taskId)
        Log.i(TAG, "$where display=$id expected=${session.expectedDisplayId} task=$taskId")
        if (id == Display.DEFAULT_DISPLAY) return
        if (startedTarget) return
        startedTarget = true
        val error = startTarget(session.packageName, session.activityName)
        if (error != null) {
            session.fail(error)
        } else {
            session.succeed(id, taskId)
        }
        window.decorView.post { finish() }
    }

    private fun startTarget(pkg: String, activityName: String): String? {
        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(pkg, activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        }
        try {
            startActivity(launch)
            return null
        } catch (first: Throwable) {
            val fallback = packageManager.getLaunchIntentForPackage(pkg)
                ?: return "Could not start $pkg: ${first.message}"
            fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            return try {
                startActivity(fallback)
                null
            } catch (second: Throwable) {
                "Could not start $pkg: ${second.message}"
            }
        }
    }

    companion object {
        private const val TAG = "GhostTrampoline"
        private const val SELF_TIMEOUT_MS = 6_000L
        private const val POLL_MS = 100L
    }
}
