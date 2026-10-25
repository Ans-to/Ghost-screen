package com.ghostdisplay.app.session

import android.view.Surface
import com.ghostdisplay.app.display.VirtualDisplayEngine

/**
 * Attaches a Viewer surface to an existing VirtualDisplay without touching
 * the hidden app's lifecycle. Closing the Viewer restores the engine surface
 * so the hidden window keeps running.
 */
object ViewerReattach {

    fun attach(engine: VirtualDisplayEngine?, sessionId: String, surface: Surface): Boolean {
        if (engine == null) return false
        if (engine.get(sessionId) == null) return false
        return engine.setSurface(sessionId, surface)
    }

    fun detach(engine: VirtualDisplayEngine?, sessionId: String): Boolean {
        if (engine == null) return false
        return engine.restoreEngineSurface(sessionId)
    }

    fun canReattach(engine: VirtualDisplayEngine?, sessionId: String): Boolean {
        return engine?.get(sessionId) != null
    }
}
