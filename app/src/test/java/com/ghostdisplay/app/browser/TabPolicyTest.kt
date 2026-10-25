package com.ghostdisplay.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TabPolicyTest {

    @Test
    fun defaultPoolIsTenHardCapSixteen() {
        assertThat(TabPolicy.DEFAULT_MAX_TABS).isEqualTo(10)
        assertThat(TabPolicy.HARD_MAX_TABS).isEqualTo(16)
        assertThat(TabPolicy.clampMaxTabs(99)).isEqualTo(16)
        assertThat(TabPolicy.clampMaxTabs(0)).isEqualTo(1)
    }

    @Test
    fun tenthTabIsAllowed() {
        val d = TabPolicy.decideNewTab(openCount = 9, maxTabs = 10, deviceRamGb = 8.0)
        assertThat(d).isInstanceOf(TabPolicy.NewTabDecision.Allow::class.java)
    }

    @Test
    fun eleventhTabRejectedAtDefaultCap() {
        val d = TabPolicy.decideNewTab(openCount = 10, maxTabs = 10, deviceRamGb = 8.0)
        assertThat(d).isInstanceOf(TabPolicy.NewTabDecision.Reject::class.java)
        assertThat((d as TabPolicy.NewTabDecision.Reject).reason).contains("Max tabs")
    }

    @Test
    fun tabNineWarns() {
        val d = TabPolicy.decideNewTab(openCount = 8, maxTabs = 16, deviceRamGb = 3.0)
        assertThat(d).isInstanceOf(TabPolicy.NewTabDecision.Allow::class.java)
        val w = (d as TabPolicy.NewTabDecision.Allow).warnings
        assertThat(w.joinToString()).contains("under 4 GB")
    }

    @Test
    fun tabElevenPlusWarnsWhenCapRaised() {
        val d = TabPolicy.decideNewTab(openCount = 10, maxTabs = 16, deviceRamGb = 8.0)
        val w = (d as TabPolicy.NewTabDecision.Allow).warnings
        assertThat(w.joinToString()).contains("above the default pool of 10")
    }

    @Test
    fun hardCapRejectsAtSixteen() {
        val d = TabPolicy.decideNewTab(openCount = 16, maxTabs = 16, deviceRamGb = 12.0)
        assertThat(d).isInstanceOf(TabPolicy.NewTabDecision.Reject::class.java)
        assertThat((d as TabPolicy.NewTabDecision.Reject).reason).contains("Hard cap")
    }

    @Test
    fun closeRequiresConfirmation() {
        assertThat(TabPolicy.canClose(pinned = false, userConfirmed = false, closePinnedExplicit = false)).isFalse()
        assertThat(TabPolicy.canClose(pinned = false, userConfirmed = true, closePinnedExplicit = false)).isTrue()
    }

    @Test
    fun pinnedRequiresExplicitClose() {
        assertThat(TabPolicy.canClose(pinned = true, userConfirmed = true, closePinnedExplicit = false)).isFalse()
        assertThat(TabPolicy.canClose(pinned = true, userConfirmed = true, closePinnedExplicit = true)).isTrue()
    }

    @Test
    fun restoreOrderPinsFirstThenRecent() {
        fun tab(id: String, pin: Boolean, active: Long, idx: Int) = TabRecord(
            id, "s", "https://x/$id", id, null, 0, 0, null, null, false, false,
            false, pin, false, true, false, false, 0, active, null, idx, TabLiveStatus.LIVE,
        )
        val ordered = TabPolicy.restoreOrder(
            listOf(
                tab("a", false, 10, 0),
                tab("b", true, 1, 1),
                tab("c", false, 50, 2),
                tab("d", true, 40, 3),
            ),
        )
        assertThat(ordered.map { it.id }).isEqualTo(listOf("d", "b", "c", "a"))
    }

    @Test
    fun closeOthersSkipsPinnedAndKeep() {
        fun tab(id: String, pin: Boolean) = TabRecord(
            id, "s", "u", id, null, 0, 0, null, null, false, false,
            false, pin, false, true, false, false, 0, 0, null, 0, TabLiveStatus.LIVE,
        )
        val ids = TabPolicy.closeOthersIds(
            listOf(tab("keep", false), tab("pin", true), tab("x", false), tab("y", false)),
            "keep",
        )
        assertThat(ids).containsExactly("x", "y")
    }

    @Test
    fun neverParksWhenMemoryHealthy() {
        assertThat(TabPolicy.shouldParkRestores(false, 800_000_000, 200_000_000)).isFalse()
    }

    @Test
    fun parksWhenLowMemory() {
        assertThat(TabPolicy.shouldParkRestores(true, 800_000_000, 200_000_000)).isTrue()
        assertThat(TabPolicy.shouldParkRestores(false, 50_000_000, 200_000_000)).isTrue()
    }

    @Test
    fun memoryBarClamps() {
        assertThat(TabPolicy.memoryBarFraction(50, 100)).isEqualTo(0.5f)
        assertThat(TabPolicy.memoryBarFraction(200, 100)).isEqualTo(1f)
        assertThat(TabPolicy.memoryBarFraction(10, 0)).isEqualTo(0f)
    }
}
