package com.ghostdisplay.app.profile

import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.display.DisplayConfig
import com.ghostdisplay.app.display.PhysicalMetrics

/**
 * Auto-generated defaults for every launchable app. Overrides live in Room.
 * The engine consumes [AppProfile] the same way for every package.
 */
object PerAppProfileManager {

    fun defaultProfile(
        app: LaunchableApp,
        physical: PhysicalMetrics,
        isSystemApp: Boolean = false,
    ): AppProfile {
        val category = AppCategoryClassifier.classify(
            app.packageName, app.label, app.requestedOrientation, isSystemApp,
        )
        val probe = CompatibilityProbe.preLaunch(
            category,
            drmSecureEnabled = category == AppCategory.STREAMING_DRM,
        )
        return AppProfile(
            packageName = app.packageName,
            widthPx = null,
            heightPx = null,
            densityDpi = null,
            orientation = DisplayConfig.Orientation.AUTO.name,
            surfaceMode = DisplayConfig.SurfaceMode.MEDIA_PROJECTION.name,
            secure = category == AppCategory.STREAMING_DRM,
            audioEnabled = true,
            blockNotifications = false,
            compatibility = probe.compatibility.name,
            lastError = probe.caveat,
            lastNoticesJson = probe.caveat,
        )
    }

    fun mergeOverride(stored: AppProfile?, generated: AppProfile): AppProfile {
        if (stored == null) return generated
        return stored.copy(
            compatibility = stored.compatibility.ifBlank { generated.compatibility },
            lastError = stored.lastError ?: generated.lastError,
        )
    }
}
