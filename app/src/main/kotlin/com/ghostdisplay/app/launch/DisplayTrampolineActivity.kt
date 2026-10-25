package com.ghostdisplay.app.launch

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Display

/**
 * GhostDisplay activity that is already on the virtual display.
 *
 * Xiaomi / HyperOS rejects startActivity of *another* package with
 * launchDisplayId. It does allow our own activity on a display we own.
 * Chrome is then started with no launchDisplayId so it inherits this
 * activity's display.
 */
class DisplayTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val activityName = intent.getStringExtra(EXTRA_ACTIVITY)
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (pkg.isNullOrBlank() || activityName.isNullOrBlank() || displayId == Display.DEFAULT_DISPLAY) {
            finish()
            return
        }
        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(pkg, activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        }
        try {
            startActivity(launch)
        } catch (_: Throwable) {
            val fallback = packageManager.getLaunchIntentForPackage(pkg)
            if (fallback != null) {
                fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                startActivity(fallback)
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_ACTIVITY = "activity"
    }
}
