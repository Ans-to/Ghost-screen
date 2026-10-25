package com.ghostdisplay.app.launch

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.ghostdisplay.app.data.LaunchableApp

/**
 * Enumerates launchable apps via ACTION_MAIN + CATEGORY_LAUNCHER.
 * Isolated so tests can stub [query].
 */
class AppCatalog(private val packageManager: PackageManager) {

    fun listLaunchable(selfPackage: String): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = query(intent)
        return resolved.mapNotNull { info ->
            val ai = info.activityInfo ?: return@mapNotNull null
            if (ai.packageName == selfPackage) return@mapNotNull null
            LaunchableApp(
                packageName = ai.packageName,
                activityName = ai.name,
                label = info.loadLabel(packageManager)?.toString() ?: ai.packageName,
                requestedOrientation = ai.requestedOrientation,
                resizeable = isResizeable(ai),
            )
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun find(packageName: String, selfPackage: String): LaunchableApp? =
        listLaunchable(selfPackage).firstOrNull { it.packageName == packageName }

    fun requestedOrientation(packageName: String, activityName: String?): Int {
        return try {
            if (activityName != null) {
                val info = packageManager.getActivityInfo(
                    android.content.ComponentName(packageName, activityName),
                    0,
                )
                info.requestedOrientation
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } catch (_: Exception) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun query(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun isResizeable(ai: ActivityInfo): Boolean {
        return try {
            val field = ActivityInfo::class.java.getField("resizeMode")
            val mode = field.getInt(ai)
            // RESIZE_MODE_UNRESIZEABLE = 0
            mode != 0
        } catch (_: Exception) {
            true
        }
    }
}
