package com.ghostdisplay.app.profile

import com.ghostdisplay.app.data.Compatibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompatibilityProbeTest {

    @Test
    fun drmWithoutSecureIsUnsupportedBeforeLaunch() {
        val result = CompatibilityProbe.preLaunch(AppCategory.STREAMING_DRM, drmSecureEnabled = false)
        assertThat(result.compatibility).isEqualTo(Compatibility.UNSUPPORTED)
        assertThat(result.preLaunchUnsupported).isTrue()
    }

    @Test
    fun messagingIsSupportedByDefault() {
        val result = CompatibilityProbe.preLaunch(AppCategory.MESSAGING, drmSecureEnabled = false)
        assertThat(result.compatibility).isEqualTo(Compatibility.SUPPORTED)
    }

    @Test
    fun financeAndGamesAreLimitedNotSpecialCased() {
        assertThat(CompatibilityProbe.preLaunch(AppCategory.FINANCE, false).compatibility)
            .isEqualTo(Compatibility.LIMITED)
        assertThat(CompatibilityProbe.preLaunch(AppCategory.GAME, false).compatibility)
            .isEqualTo(Compatibility.LIMITED)
    }

    @Test
    fun leakOntoDefaultDisplayIsUnsupported() {
        val after = CompatibilityProbe.afterLaunch(
            category = AppCategory.SOCIAL,
            launchedOnVirtualDisplay = true,
            verifiedOnVirtualDisplay = false,
            appearedOnDefaultDisplay = true,
            drmStillRefused = false,
        )
        assertThat(after.compatibility).isEqualTo(Compatibility.UNSUPPORTED)
    }

    @Test
    fun verifiedSocialIsSupported() {
        val after = CompatibilityProbe.afterLaunch(
            category = AppCategory.SOCIAL,
            launchedOnVirtualDisplay = true,
            verifiedOnVirtualDisplay = true,
            appearedOnDefaultDisplay = false,
            drmStillRefused = false,
        )
        assertThat(after.compatibility).isEqualTo(Compatibility.SUPPORTED)
    }

    @Test
    fun caveatNeverUsesBannedWording() {
        AppCategory.values().forEach { cat ->
            val text = CompatibilityProbe.caveatFor(cat).lowercase()
            assertThat(text).doesNotContain("clone")
            assertThat(text).doesNotContain("copy")
            assertThat(text).doesNotContain("instance")
            assertThat(text).doesNotContain("duplicate")
        }
    }
}
