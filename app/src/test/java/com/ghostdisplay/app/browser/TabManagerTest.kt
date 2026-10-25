package com.ghostdisplay.app.browser

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TabManagerTest {

    @Test
    fun tenTabsStayAliveAndCloseFiveOnlyKillsFive() = runTest {
        val store = InMemoryTabStore()
        val host = FakeHost()
        val tm = TabManager(store, host, clock = { 1_000L }, ramGbProvider = { 8.0 })
        tm.attachExisting()
        tm.setMaxTabs(10)
        val ids = mutableListOf<String>()
        repeat(10) { i ->
            val r = tm.newTab("https://example.com/t$i")
            assertThat(r).isInstanceOf(TabOpResult.Ok::class.java)
            ids += (r as TabOpResult.Ok).tab.id
        }
        assertThat(tm.tabs.value).hasSize(10)
        assertThat(host.live).hasSize(10)

        val fifth = ids[4]
        val closed = tm.closeTab(fifth, userConfirmed = true)
        assertThat(closed).isInstanceOf(TabOpResult.Ok::class.java)
        assertThat(tm.tabs.value).hasSize(9)
        assertThat(tm.tabs.value.map { it.id }).doesNotContain(fifth)
        assertThat(host.destroyed).containsExactly(fifth)
        assertThat(store.tabs.keys).doesNotContain(fifth)
        assertThat(host.live).hasSize(9)
    }

    @Test
    fun unconfirmedCloseIsRejectedAndRecordRemains() = runTest {
        val store = InMemoryTabStore()
        val host = FakeHost()
        val tm = TabManager(store, host, ramGbProvider = { 8.0 })
        tm.attachExisting()
        val id = (tm.newTab("https://a.example") as TabOpResult.Ok).tab.id
        val r = tm.closeTab(id, userConfirmed = false)
        assertThat(r).isInstanceOf(TabOpResult.Rejected::class.java)
        assertThat(tm.tabs.value).hasSize(1)
        assertThat(host.destroyed).isEmpty()
    }

    @Test
    fun restoreNeverDropsRecordsEvenWhenHostFails() = runTest {
        val store = InMemoryTabStore()
        val host = FakeHost()
        val tm = TabManager(store, host, ramGbProvider = { 8.0 })
        tm.attachExisting()
        tm.setMaxTabs(10)
        repeat(5) { tm.newTab("https://ex.example/t$it") }
        val ids = tm.tabs.value.map { it.id }.toSet()
        host.live.clear()
        host.failAlways = true
        val report = tm.restoreAll(parkPredicate = { false }, reason = "force-stop")
        assertThat(report.droppedAny(ids)).isFalse()
        assertThat(report.allRecords.map { it.id }.toSet()).isEqualTo(ids)
        assertThat(store.tabs.keys).isEqualTo(ids)
        assertThat(tm.tabs.value).hasSize(5)
        assertThat(tm.tabs.value.all { it.liveStatus == TabLiveStatus.PAUSED_BY_SYSTEM }).isTrue()
    }

    @Test
    fun restoreParksWhenPredicateSaysSoButKeepsRecords() = runTest {
        val store = InMemoryTabStore()
        val host = FakeHost()
        val tm = TabManager(store, host, ramGbProvider = { 2.0 })
        tm.attachExisting()
        tm.setMaxTabs(10)
        repeat(4) { tm.newTab("https://ex.example/t$it") }
        host.live.clear()
        val report = tm.restoreAll(parkPredicate = { live -> live >= 1 }, reason = "low-mem")
        assertThat(report.live).hasSize(1)
        assertThat(report.pausedBySystem).hasSize(3)
        assertThat(report.droppedAny(store.tabs.keys)).isFalse()
        assertThat(report.notices.any { it.contains("paused by system") }).isTrue()
    }

    @Test
    fun duplicateCreatesSecondLiveTab() = runTest {
        val store = InMemoryTabStore()
        val host = FakeHost()
        val tm = TabManager(store, host, ramGbProvider = { 8.0 })
        tm.attachExisting()
        val a = (tm.newTab("https://dup.example") as TabOpResult.Ok).tab
        val b = tm.duplicate(a.id) as TabOpResult.Ok
        assertThat(b.tab.id).isNotEqualTo(a.id)
        assertThat(b.tab.url).isEqualTo(a.url)
        assertThat(tm.tabs.value).hasSize(2)
    }

    @Test
    fun loadUrlNormalizesAndLogs() = runTest {
        val store = InMemoryTabStore()
        val host = FakeHost()
        val tm = TabManager(store, host, ramGbProvider = { 8.0 })
        tm.attachExisting()
        val id = (tm.newTab("example.com") as TabOpResult.Ok).tab.id
        tm.loadUrl(id, "news.ycombinator.com")
        assertThat(host.live[id]?.url).isEqualTo("https://news.ycombinator.com")
        assertThat(store.logs.any { it.event == TabLogEvent.NAVIGATED }).isTrue()
    }
}
