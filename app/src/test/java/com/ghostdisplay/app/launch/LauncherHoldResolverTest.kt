package com.ghostdisplay.app.launch

import com.ghostdisplay.app.data.LaunchableApp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LauncherHoldResolverTest {

    private val chrome = app("com.android.chrome", "Chrome")
    private val firefox = app("org.mozilla.firefox", "Firefox")
    private val whatsapp = app("com.whatsapp", "WhatsApp")
    private val apps = listOf(chrome, firefox, whatsapp)
    private val homes = setOf("com.google.android.apps.nexuslauncher", "com.android.launcher3")
    private val self = "com.ghostdisplay.app.debug"

    @Test
    fun homeLongPressOnChromeResolves() {
        val hit = LauncherHoldResolver.resolve(
            HoldCandidate(homes.first(), "Chrome", null, "android.widget.TextView"),
            apps, homes, self,
        )
        assertThat(hit).isEqualTo(chrome)
    }

    @Test
    fun stopPathUsesSameResolver() {
        val hit = LauncherHoldResolver.resolve(
            HoldCandidate(homes.last(), "WhatsApp", null, null),
            apps, homes, self,
        )
        assertThat(hit?.packageName).isEqualTo("com.whatsapp")
    }

    @Test
    fun ignoresLongPressInsideGhostDisplay() {
        val hit = LauncherHoldResolver.resolve(
            HoldCandidate(self, "Chrome", null, null),
            apps, homes + self, self,
        )
        assertThat(hit).isNull()
    }

    @Test
    fun ignoresLongPressInsideOtherApps() {
        val hit = LauncherHoldResolver.resolve(
            HoldCandidate("com.whatsapp", "Chrome", null, null),
            apps, homes, self,
        )
        assertThat(hit).isNull()
    }

    @Test
    fun ignoresFoldersAndWidgets() {
        assertThat(
            LauncherHoldResolver.resolve(
                HoldCandidate(homes.first(), "Folder: Social", null, null),
                apps, homes, self,
            ),
        ).isNull()
        assertThat(
            LauncherHoldResolver.resolve(
                HoldCandidate(homes.first(), "Chrome", null, "android.appwidget.AppWidgetHostView"),
                apps, homes, self,
            ),
        ).isNull()
    }

    @Test
    fun ignoresLauncherChromeLabels() {
        assertThat(
            LauncherHoldResolver.resolve(
                HoldCandidate(homes.first(), "Search", null, null),
                apps, homes, self,
            ),
        ).isNull()
    }

    @Test
    fun notificationSuffixStillMatches() {
        val hit = LauncherHoldResolver.resolve(
            HoldCandidate(homes.first(), "Chrome, 3 notifications", null, null),
            apps, homes, self,
        )
        assertThat(hit).isEqualTo(chrome)
    }

    @Test
    fun ambiguousLabelDoesNotGuess() {
        val twins = listOf(
            app("com.a.chrome", "Chrome"),
            app("com.b.chrome", "Chrome"),
        )
        val hit = LauncherHoldResolver.resolve(
            HoldCandidate(homes.first(), "Chrome", null, null),
            twins, homes, self,
        )
        assertThat(hit).isNull()
    }

    @Test
    fun emptyLabelIgnored() {
        val hit = LauncherHoldResolver.resolve(
            HoldCandidate(homes.first(), "  ", null, null),
            apps, homes, self,
        )
        assertThat(hit).isNull()
    }

    private fun app(pkg: String, label: String) = LaunchableApp(
        packageName = pkg,
        activityName = "$pkg.Main",
        label = label,
        requestedOrientation = -1,
        resizeable = true,
    )
}
