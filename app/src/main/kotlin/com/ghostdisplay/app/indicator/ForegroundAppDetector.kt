package com.ghostdisplay.app.indicator

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.ghostdisplay.app.control.GhostAccessibilityService

/**
 * Which package is in the foreground on the physical display.
 * Prefers UsageStats; falls back to Accessibility window events.
 */
class ForegroundAppDetector(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun foregroundPackage(): String? {
        usageForeground()?.let { return it }
        GhostAccessibilityService.lastForegroundPackage?.let { return it }
        return accessibilityForeground()
    }

    fun isHomeOrLauncher(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = context.packageManager.resolveActivity(home, 0)
        val launcher = ri?.activityInfo?.packageName
        return packageName == launcher ||
            packageName.contains("launcher") ||
            packageName == "com.android.systemui"
    }

    fun isGhostDisplay(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName == context.packageName
    }

    fun usageSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    private fun usageForeground(): String? {
        if (!hasUsageAccess()) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                last = event.packageName
            }
        }
        return last
    }

    private fun accessibilityForeground(): String? {
        val service = GhostAccessibilityService.instance ?: return null
        val windows = service.windows() ?: return null
        val defaultWindows = windows.filter {
            if (Build.VERSION.SDK_INT >= 30) it.displayId == 0 else true
        }
        return defaultWindows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.root?.packageName?.toString()
    }

    companion object {
        const val ACTIVITY_RESUMED_FALLBACK = 23
    }
}
