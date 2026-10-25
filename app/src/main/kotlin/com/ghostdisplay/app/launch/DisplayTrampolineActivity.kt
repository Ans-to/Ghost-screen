package com.ghostdisplay.app.launch

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Display

/**
 * GhostDisplay's own activity, started on the virtual display. From here the
 * target app is started as a child of a caller that already lives on that
 * display — which Android allows even when display-0 is forbidden from
 * placing a foreign task on a virtual display.
 *
 * Never opened on the phone screen: if we landed on display 0, we abort.
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
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        try {
            startActivity(launch, options.toBundle())
        } catch (_: Throwable) {
            val fallback = packageManager.getLaunchIntentForPackage(pkg)
            if (fallback != null) {
                fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                startActivity(fallback, options.toBundle())
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_ACTIVITY = "activity"
    }
}
