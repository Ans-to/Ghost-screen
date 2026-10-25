package com.ghostdisplay.app.control

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ONLY control path for hidden apps. Declared in the manifest; the user
 * enables it with the built-in Settings toggle. No other app is involved.
 */
class GhostAccessibilityService : AccessibilityService() {

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound.asStateFlow()

    /**
     * Gesture dispatch has no public display-id parameter. On Android 11+ we
     * still attempt it, but [gesturesUsable] is a capability we expose so the
     * UI can label view-only instead of failing silently. We start optimistic
     * and flip to false if dispatchGesture returns false once.
     */
    @Volatile
    var gesturesUsable: Boolean = true
        internal set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _bound.value = true
        // Touch exploration would hijack the physical screen. Do not enable it.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            lastForegroundPackage = pkg
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        _bound.value = false
        super.onDestroy()
    }

    fun windows(): List<AccessibilityWindowInfo>? {
        return try {
            windows
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        @Volatile
        var instance: GhostAccessibilityService? = null
            private set

        @Volatile
        var lastForegroundPackage: String? = null

        fun isEnabled(): Boolean = instance != null

        /**
         * API 30 is the first version that reports [AccessibilityWindowInfo.getDisplayId].
         * On API 29 we cannot tell which display a window belongs to, so virtual-display
         * targeting is degraded — and we say so.
         */
        fun canObserveDisplayId(): Boolean = Build.VERSION.SDK_INT >= 30
    }
}
