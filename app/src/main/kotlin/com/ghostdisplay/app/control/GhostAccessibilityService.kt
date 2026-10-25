package com.ghostdisplay.app.control

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.launch.AppCatalog
import com.ghostdisplay.app.launch.HoldCandidate
import com.ghostdisplay.app.launch.LauncherHoldResolver
import com.ghostdisplay.app.ui.HoldActionActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ONLY control path for hidden apps. Declared in the manifest; the user
 * enables it with the built-in Settings toggle. No other app is involved.
 *
 * After that one-time grant it also watches home-screen long-presses so the
 * user can start or stop a hidden window without opening GhostDisplay.
 */
class GhostAccessibilityService : AccessibilityService() {

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound.asStateFlow()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastHoldAt = 0L
    private var cachedApps: List<LaunchableApp> = emptyList()
    private var cachedAppsAt = 0L
    private var cachedHomes: Set<String> = emptySet()
    private var cachedHomesAt = 0L

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
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return
        if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            ev.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            lastForegroundPackage = pkg
        }
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            handleHomeHold(ev)
        }
    }

    private fun handleHomeHold(event: AccessibilityEvent) {
        val now = SystemClock.uptimeMillis()
        if (now - lastHoldAt < 800) return
        val candidate = try {
            candidateOf(event)
        } catch (_: Throwable) {
            null
        } ?: return
        val hit = try {
            LauncherHoldResolver.resolve(
                candidate,
                launchableCached(),
                homePackagesCached(),
                packageName,
            )
        } catch (_: Throwable) {
            null
        } ?: return
        lastHoldAt = now
        mainHandler.post {
            try {
                startActivity(
                    HoldActionActivity.intent(
                        this,
                        hit.packageName,
                        hit.activityName,
                        hit.label,
                    ),
                )
            } catch (_: Throwable) {
                // If the OEM blocks background activities, in-app hold still works.
            }
        }
    }

    private fun candidateOf(event: AccessibilityEvent): HoldCandidate? {
        val eventPackage = event.packageName?.toString() ?: return null
        var desc = event.contentDescription?.toString()
        var text = event.text?.joinToString(" ")?.ifBlank { null }
        var className = event.className?.toString()
        val source = event.source
        if (source != null) {
            try {
                val fromNode = labelsOf(source)
                if (desc.isNullOrBlank()) desc = fromNode.first
                if (text.isNullOrBlank()) text = fromNode.second
                if (className.isNullOrBlank()) className = source.className?.toString()
            } finally {
                source.recycle()
            }
        }
        return HoldCandidate(eventPackage, desc, text, className)
    }

    private fun labelsOf(node: AccessibilityNodeInfo): Pair<String?, String?> {
        val desc = node.contentDescription?.toString()
        val text = node.text?.toString()
        if (!desc.isNullOrBlank() || !text.isNullOrBlank()) return desc to text
        val parent = node.parent ?: return null to null
        return try {
            parent.contentDescription?.toString() to parent.text?.toString()
        } finally {
            parent.recycle()
        }
    }

    private fun launchableCached(): List<LaunchableApp> {
        val now = SystemClock.uptimeMillis()
        if (now - cachedAppsAt < 15_000 && cachedApps.isNotEmpty()) return cachedApps
        cachedApps = try {
            AppCatalog(packageManager).listLaunchable(packageName)
        } catch (_: Throwable) {
            cachedApps
        }
        cachedAppsAt = now
        return cachedApps
    }

    private fun homePackagesCached(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - cachedHomesAt < 60_000 && cachedHomes.isNotEmpty()) return cachedHomes
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        cachedHomes = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }.map { it.activityInfo.packageName }.toSet()
        cachedHomesAt = now
        return cachedHomes
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
