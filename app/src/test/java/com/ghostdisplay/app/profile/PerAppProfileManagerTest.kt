package com.ghostdisplay.app.profile

import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.display.DisplayConfig
import com.ghostdisplay.app.display.PhysicalMetrics
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerAppProfileManagerTest {

    private val physical = PhysicalMetrics(1080, 2400, 420)

    private fun app(pkg: String, label: String) = LaunchableApp(
        packageName = pkg,
        activityName = "$pkg.Main",
        label = label,
        requestedOrientation = -1,
        resizeable = true,
    )

    @Test
    fun defaultsArePhysicalSizedMediaProjectionWithAudio() {
        val p = PerAppProfileManager.defaultProfile(app("com.example.foo", "Foo"), physical)
        assertThat(p.widthPx).isNull()
        assertThat(p.heightPx).isNull()
        assertThat(p.densityDpi).isNull()
        assertThat(p.surfaceMode).isEqualTo(DisplayConfig.SurfaceMode.MEDIA_PROJECTION.name)
        assertThat(p.audioEnabled).isTrue()
        assertThat(p.secure).isFalse()
    }

    @Test
    fun drmGetsSecureFlag() {
        val p = PerAppProfileManager.defaultProfile(app("com.netflix.mediaclient", "Netflix"), physical)
        assertThat(p.secure).isTrue()
    }

    @Test
    fun mergeKeepsStoredOverrides() {
        val generated = PerAppProfileManager.defaultProfile(app("com.example.foo", "Foo"), physical)
        val stored = generated.copy(orientation = DisplayConfig.Orientation.LANDSCAPE.name, lastError = null)
        val merged = PerAppProfileManager.mergeOverride(stored, generated)
        assertThat(merged.orientation).isEqualTo(DisplayConfig.Orientation.LANDSCAPE.name)
        assertThat(merged.lastError).isEqualTo(generated.lastError)
    }

    @Test
    fun enginePathDoesNotSpecialCaseAPackage() {
        val a = PerAppProfileManager.defaultProfile(app("com.instagram.android", "Instagram"), physical)
        val b = PerAppProfileManager.defaultProfile(app("com.example.other", "Other"), physical)
        assertThat(a.surfaceMode).isEqualTo(b.surfaceMode)
        assertThat(a.audioEnabled).isEqualTo(b.audioEnabled)
    }
}
