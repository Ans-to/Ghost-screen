package com.ghostdisplay.app.indicator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OverlayPolicyTest {

    private fun base(
        optIn: Boolean = true,
        granted: Boolean = true,
        hiddenElapsed: Boolean = true,
        viewer: Boolean = false,
        fg: String? = "com.example.app",
        hidden: String? = "com.example.app",
        home: Boolean = false,
        ghost: Boolean = false,
    ) = OverlayPolicy.Input(
        overlayOptIn = optIn,
        overlayGranted = granted,
        hiddenUntilElapsed = hiddenElapsed,
        viewerOpen = viewer,
        foregroundPackage = fg,
        hiddenSessionPackage = hidden,
        isHomeOrLauncher = home,
        isGhostDisplay = ghost,
    )

    @Test
    fun shownOnlyWhenForegroundMatchesHiddenApp() {
        assertThat(OverlayPolicy.shouldShow(base())).isTrue()
        assertThat(OverlayPolicy.shouldShow(base(fg = "com.other", hidden = "com.example.app"))).isFalse()
    }

    @Test
    fun neverWithoutOptInOrGrant() {
        assertThat(OverlayPolicy.shouldShow(base(optIn = false))).isFalse()
        assertThat(OverlayPolicy.shouldShow(base(granted = false))).isFalse()
    }

    @Test
    fun neverOverHomeViewerOrGhostDisplay() {
        assertThat(OverlayPolicy.shouldShow(base(home = true))).isFalse()
        assertThat(OverlayPolicy.shouldShow(base(viewer = true))).isFalse()
        assertThat(OverlayPolicy.shouldShow(base(ghost = true))).isFalse()
    }

    @Test
    fun hiddenUntilEndOfDaySuppresses() {
        assertThat(OverlayPolicy.shouldShow(base(hiddenElapsed = false))).isFalse()
    }

    @Test
    fun endOfDayIsInTheFuture() {
        val now = 1_700_000_000_000L
        assertThat(OverlayPolicy.endOfDayMillis(now)).isGreaterThan(now)
    }
}
