package com.ghostdisplay.app.launch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KnownLimitsTest {

    @Test
    fun netflixIsDrm() {
        val s = KnownLimits.forApp("com.netflix.mediaclient", "Netflix")
        assertThat(s).contains("DRM")
        assertThat(KnownLimits.categoryOf("com.netflix.mediaclient", "Netflix")).contains("DRM")
    }

    @Test
    fun chromeIsBrowser() {
        assertThat(KnownLimits.categoryOf("com.android.chrome", "Chrome")).isEqualTo("Browser")
    }

    @Test
    fun matrixHasRequiredCategories() {
        val cats = KnownLimits.matrix.map { it.first }
        assertThat(cats).containsAtLeast("Browser", "Video player", "Streaming (YouTube)", "Game (landscape)", "Social", "Banking / payments")
    }
}
