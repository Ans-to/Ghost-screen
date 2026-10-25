package com.ghostdisplay.app.launch

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.View

/**
 * Puts a GhostDisplay window on the virtual display so our UID is present
 * there before we start our own starter activity. AOSP allows launching onto
 * a display when the caller uid already has a window on it.
 */
class OccupancyPresentation(
    outerContext: Context,
    display: Display,
) : Presentation(outerContext, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = View(context)
        view.setBackgroundColor(0x01000000)
        setContentView(view)
    }
}
