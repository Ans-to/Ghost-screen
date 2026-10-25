package com.ghostdisplay.app.display

/**
 * How the virtual display should be sized and backed. Pure data — no Android
 * types — so [DisplayConfigResolver] and unit tests can share it.
 */
data class DisplayConfig(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val orientation: Orientation,
    val surfaceMode: SurfaceMode,
    val secure: Boolean,
    val audioEnabled: Boolean,
    val name: String = "GhostDisplay",
) {
    enum class Orientation { AUTO, PORTRAIT, LANDSCAPE }

    enum class SurfaceMode {
        /** Lighter ImageReader / SurfaceTexture surface. */
        IMAGE_READER,

        /** MediaProjection-backed surface — required for video, DRM-ish apps, audio capture. */
        MEDIA_PROJECTION,
    }

    init {
        require(widthPx > 0 && heightPx > 0) { "Display size must be positive" }
        require(densityDpi > 0) { "DPI must be positive" }
    }

    val isLandscape: Boolean get() = widthPx > heightPx
}

data class PhysicalMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
)

/**
 * Resolves a [DisplayConfig] from an optional per-app override plus the
 * physical display and the target activity's requested orientation.
 *
 * Landscape games / streaming apps get a swapped buffer so the virtual
 * display matches what they asked for, even if the phone is currently
 * held in portrait.
 */
object DisplayConfigResolver {

    fun resolve(
        physical: PhysicalMetrics,
        requestedOrientation: Int?,
        overrideWidth: Int? = null,
        overrideHeight: Int? = null,
        overrideDpi: Int? = null,
        orientation: DisplayConfig.Orientation = DisplayConfig.Orientation.AUTO,
        surfaceMode: DisplayConfig.SurfaceMode = DisplayConfig.SurfaceMode.IMAGE_READER,
        secure: Boolean = false,
        audioEnabled: Boolean = true,
        name: String = "GhostDisplay",
    ): DisplayConfig {
        val physW = overrideWidth ?: physical.widthPx
        val physH = overrideHeight ?: physical.heightPx
        val dpi = overrideDpi ?: physical.densityDpi

        val wantLandscape = when (orientation) {
            DisplayConfig.Orientation.LANDSCAPE -> true
            DisplayConfig.Orientation.PORTRAIT -> false
            DisplayConfig.Orientation.AUTO -> isLandscapeRequest(requestedOrientation, physW, physH)
        }

        val (w, h) = forceOrientation(physW, physH, wantLandscape)
        return DisplayConfig(
            widthPx = w,
            heightPx = h,
            densityDpi = dpi.coerceIn(80, 640),
            orientation = if (wantLandscape) DisplayConfig.Orientation.LANDSCAPE else DisplayConfig.Orientation.PORTRAIT,
            surfaceMode = surfaceMode,
            secure = secure,
            audioEnabled = audioEnabled,
            name = name,
        )
    }

    fun forceOrientation(width: Int, height: Int, landscape: Boolean): Pair<Int, Int> {
        val min = minOf(width, height)
        val max = maxOf(width, height)
        return if (landscape) max to min else min to max
    }

    /**
     * ActivityInfo requestedOrientation constants duplicated so this file stays
     * Android-free for unit tests.
     *
     * SCREEN_ORIENTATION_LANDSCAPE = 0
     * SCREEN_ORIENTATION_PORTRAIT = 1
     * SCREEN_ORIENTATION_SENSOR_LANDSCAPE = 6
     * SCREEN_ORIENTATION_SENSOR_PORTRAIT = 7
     * SCREEN_ORIENTATION_REVERSE_LANDSCAPE = 8
     * SCREEN_ORIENTATION_REVERSE_PORTRAIT = 9
     * SCREEN_ORIENTATION_USER_LANDSCAPE = 11
     * SCREEN_ORIENTATION_USER_PORTRAIT = 12
     */
    fun isLandscapeRequest(requestedOrientation: Int?, physicalW: Int, physicalH: Int): Boolean {
        return when (requestedOrientation) {
            0, 6, 8, 11 -> true
            1, 7, 9, 12 -> false
            else -> physicalW > physicalH
        }
    }
}
