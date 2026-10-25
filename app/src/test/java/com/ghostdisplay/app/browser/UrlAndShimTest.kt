package com.ghostdisplay.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlAndShimTest {

    @Test
    fun addsHttpsToBareDomain() {
        assertThat(UrlNormalizer.normalize("example.com")).isEqualTo("https://example.com")
    }

    @Test
    fun searchForWords() {
        assertThat(UrlNormalizer.normalize("hello world")).contains("google.com/search")
    }

    @Test
    fun blocksJavascriptAndFile() {
        assertThat(UrlNormalizer.normalize("javascript:alert(1)")).isEqualTo("about:blank")
        assertThat(UrlNormalizer.normalize("file:///etc/passwd")).isEqualTo("about:blank")
    }

    @Test
    fun emptyIsAboutBlank() {
        assertThat(UrlNormalizer.normalize("  ")).isEqualTo("about:blank")
    }

    @Test
    fun backStackRoundTrip() {
        val encoded = BackStackCodec.push(null, "https://a.example")
        val again = BackStackCodec.push(encoded, "https://b.example")
        assertThat(BackStackCodec.decode(again)).containsExactly("https://a.example", "https://b.example").inOrder()
        assertThat(BackStackCodec.decode(null)).isEmpty()
    }

    @Test
    fun visibilityShimOverridesHiddenAndVisibilityState() {
        assertThat(VisibilityShimInjector.SCRIPT).contains("visibilityState")
        assertThat(VisibilityShimInjector.SCRIPT).contains("return 'visible'")
        assertThat(VisibilityShimInjector.SCRIPT).contains("return false")
        assertThat(VisibilityShimInjector.SCRIPT).contains("visibilitychange")
        assertThat(VisibilityShimInjector.SCRIPT).contains("pagehide")
    }

    @Test
    fun drmWarningForNetflix() {
        val w = VisibilityShimInjector.drmWarningForUrl("https://www.netflix.com/title/1")
        assertThat(w).isNotNull()
        assertThat(w!!).contains("DRM")
        assertThat(VisibilityShimInjector.drmWarningForUrl("https://example.com")).isNull()
    }

    @Test
    fun memorySnapshotCountsLiteAndPaused() {
        fun tab(lite: Boolean, status: TabLiveStatus) = TabRecord(
            "x$lite$status", "s", "u", "t", null, 0, 0, null, null, false, false,
            false, false, lite, true, false, false, 0, 0, null, 0, status,
        )
        val snap = MemoryBudget.snapshot(
            availMemBytes = 1_000_000_000,
            totalMemBytes = 4L * 1024 * 1024 * 1024,
            thresholdBytes = 200_000_000,
            lowMemory = false,
            tabs = listOf(
                tab(false, TabLiveStatus.LIVE),
                tab(true, TabLiveStatus.LIVE),
                tab(false, TabLiveStatus.PAUSED_BY_SYSTEM),
            ),
        )
        assertThat(snap.liveTabs).isEqualTo(2)
        assertThat(snap.pausedTabs).isEqualTo(1)
        assertThat(snap.estimatedUsedBytes).isEqualTo(
            TabPolicy.EST_LIVE_BYTES + TabPolicy.EST_LITE_BYTES + TabPolicy.EST_PAUSED_BYTES,
        )
        assertThat(MemoryBudget.parkNext(snap.copy(lowMemory = true))).isTrue()
    }

    @Test
    fun restoreReportDetectsDrop() {
        val t = TabRecord(
            "1", "s", "u", "t", null, 0, 0, null, null, false, false,
            false, false, false, true, false, false, 0, 0, null, 0, TabLiveStatus.LIVE,
        )
        val report = RestoreReport(live = listOf(t), pausedBySystem = emptyList(), failed = emptyList(), notices = emptyList())
        assertThat(report.droppedAny(setOf("1"))).isFalse()
        assertThat(report.droppedAny(setOf("1", "missing"))).isTrue()
    }
}
