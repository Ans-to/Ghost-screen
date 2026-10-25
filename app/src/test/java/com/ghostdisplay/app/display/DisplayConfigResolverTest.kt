package com.ghostdisplay.app.display

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayConfigResolverTest {

    private val physical = PhysicalMetrics(1080, 2400, 420)

    @Test
    fun landscapeRequestSwapsBuffer() {
        val cfg = DisplayConfigResolver.resolve(
            physical = physical,
            requestedOrientation = 0, // SCREEN_ORIENTATION_LANDSCAPE
        )
        assertThat(cfg.widthPx).isEqualTo(2400)
        assertThat(cfg.heightPx).isEqualTo(1080)
        assertThat(cfg.orientation).isEqualTo(DisplayConfig.Orientation.LANDSCAPE)
    }

    @Test
    fun portraitForceKeepsPortraitEvenIfActivityWantsLandscape() {
        val cfg = DisplayConfigResolver.resolve(
            physical = physical,
            requestedOrientation = 0,
            orientation = DisplayConfig.Orientation.PORTRAIT,
        )
        assertThat(cfg.widthPx).isLessThan(cfg.heightPx)
        assertThat(cfg.orientation).isEqualTo(DisplayConfig.Orientation.PORTRAIT)
    }

    @Test
    fun overrideResolutionWins() {
        val cfg = DisplayConfigResolver.resolve(
            physical = physical,
            requestedOrientation = 1,
            overrideWidth = 1920,
            overrideHeight = 1080,
            overrideDpi = 320,
            orientation = DisplayConfig.Orientation.LANDSCAPE,
        )
        assertThat(cfg.widthPx).isEqualTo(1920)
        assertThat(cfg.heightPx).isEqualTo(1080)
        assertThat(cfg.densityDpi).isEqualTo(320)
    }

    @Test
    fun forceOrientationHelper() {
        assertThat(DisplayConfigResolver.forceOrientation(1080, 1920, true)).isEqualTo(1920 to 1080)
        assertThat(DisplayConfigResolver.forceOrientation(1920, 1080, false)).isEqualTo(1080 to 1920)
    }

    @Test
    fun unspecifiedUsesPhysicalPortraitPhoneAsPortrait() {
        val cfg = DisplayConfigResolver.resolve(
            physical = physical,
            requestedOrientation = -1,
        )
        assertThat(cfg.widthPx).isEqualTo(1080)
        assertThat(cfg.heightPx).isEqualTo(2400)
    }
}
