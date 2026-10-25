package com.ghostdisplay.app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Owns the open-tab set. The host creates/destroys WebViews; this class is
 * the only place a tab record can be deleted, and only after [closeTab] with
 * confirmation.
 */
class TabManager(
    private val store: TabStore,
    private val host: TabEngineHost,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ramGbProvider: () -> Double = { 0.0 },
) {
    private val _tabs = MutableStateFlow<List<TabRecord>>(emptyList())
    val tabs: StateFlow<List<TabRecord>> = _tabs.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _session = MutableStateFlow<BrowserSessionState?>(null)
    val session: StateFlow<BrowserSessionState?> = _session.asStateFlow()

    private val _notices = MutableStateFlow<List<String>>(emptyList())
    val notices: StateFlow<List<String>> = _notices.asStateFlow()

    val SESSION_ID: String get() = _session.value?.id ?: DEFAULT_SESSION

    suspend fun attachExisting() {
        val existing = store.session() ?: BrowserSessionState(
            id = DEFAULT_SESSION,
            active = true,
            masterMuted = false,
            maxTabs = TabPolicy.DEFAULT_MAX_TABS,
            createdAt = clock(),
            lastRestoredAt = null,
            displaySessionId = null,
        )
        _session.value = existing
        store.upsertSession(existing)
        _tabs.value = store.openTabs().sortedBy { it.sortIndex }
        _activeId.value = TabPolicy.restoreOrder(_tabs.value).firstOrNull()?.id
    }

    suspend fun setMaxTabs(max: Int) {
        val session = _session.value ?: return
        val next = session.copy(maxTabs = TabPolicy.clampMaxTabs(max))
        _session.value = next
        store.upsertSession(next)
    }

    suspend fun setDisplaySessionId(id: String?) {
        val session = _session.value ?: return
        val next = session.copy(displaySessionId = id)
        _session.value = next
        store.upsertSession(next)
    }

    suspend fun newTab(
        url: String = "about:blank",
        pin: Boolean = false,
        lite: Boolean = false,
        shim: Boolean = true,
        duplicateFrom: TabRecord? = null,
    ): TabOpResult {
        val open = _tabs.value
        val decision = TabPolicy.decideNewTab(
            openCount = open.size,
            maxTabs = _session.value?.maxTabs ?: TabPolicy.DEFAULT_MAX_TABS,
            deviceRamGb = ramGbProvider(),
        )
        if (decision is TabPolicy.NewTabDecision.Reject) {
            return TabOpResult.Rejected(decision.reason)
        }
        val warnings = (decision as TabPolicy.NewTabDecision.Allow).warnings.toMutableList()
        val now = clock()
        val id = UUID.randomUUID().toString()
        val normalized = UrlNormalizer.normalize(duplicateFrom?.url ?: url)
        val record = TabRecord(
            id = id,
            sessionId = SESSION_ID,
            url = normalized,
            title = duplicateFrom?.title ?: "",
            faviconUrl = duplicateFrom?.faviconUrl,
            scrollX = duplicateFrom?.scrollX ?: 0,
            scrollY = duplicateFrom?.scrollY ?: 0,
            formStateJson = duplicateFrom?.formStateJson,
            backStackJson = duplicateFrom?.backStackJson ?: BackStackCodec.encode(listOf(normalized)),
            canGoBack = duplicateFrom?.canGoBack ?: false,
            canGoForward = false,
            muted = duplicateFrom?.muted ?: false,
            pinned = pin || (duplicateFrom?.pinned ?: false),
            liteMode = lite || (duplicateFrom?.liteMode ?: false),
            keepVisibleShim = shim,
            wasPlayingMedia = false,
            audioPlaying = false,
            createdAt = now,
            lastActiveAt = now,
            lastRestoredAt = null,
            sortIndex = (open.maxOfOrNull { it.sortIndex } ?: -1) + 1,
            liveStatus = TabLiveStatus.LIVE,
        )
        VisibilityShimInjector.drmWarningForUrl(normalized)?.let { warnings += it }
        return when (val hostResult = host.createLiveTab(record)) {
            is HostResult.Live -> {
                val live = hostResult.tab.copy(liveStatus = TabLiveStatus.LIVE)
                persist(live)
                store.log(TabLogEvent(live.id, if (duplicateFrom != null) TabLogEvent.DUPLICATED else TabLogEvent.CREATED, live.url, now, null))
                select(live.id)
                emitWarnings(warnings)
                TabOpResult.Ok(live, warnings)
            }
            is HostResult.Failed -> {
                val paused = record.copy(liveStatus = TabLiveStatus.PAUSED_BY_SYSTEM)
                persist(paused)
                store.log(TabLogEvent(paused.id, TabLogEvent.PAUSED_BY_SYSTEM, paused.url, now, hostResult.reason))
                warnings += "Tab saved as “paused by system, tap to resume”. Record was not dropped. ${hostResult.reason}"
                emitWarnings(warnings)
                select(paused.id)
                TabOpResult.Ok(paused, warnings)
            }
        }
    }

    suspend fun closeTab(
        id: String,
        userConfirmed: Boolean,
        closePinnedExplicit: Boolean = false,
    ): TabOpResult {
        val tab = _tabs.value.firstOrNull { it.id == id }
            ?: return TabOpResult.Rejected("No tab $id")
        if (!TabPolicy.canClose(tab.pinned, userConfirmed, closePinnedExplicit)) {
            val why = when {
                !userConfirmed -> "Close cancelled. Tabs are never discarded without an explicit confirm."
                tab.pinned -> "Pinned tabs cannot be closed until you unpin them or confirm “close pinned”."
                else -> "Close rejected."
            }
            return TabOpResult.Rejected(why)
        }
        host.destroyTab(id)
        store.deleteTab(id)
        store.log(TabLogEvent(id, TabLogEvent.CLOSED, tab.url, clock(), "user confirmed close"))
        val remaining = _tabs.value.filter { it.id != id }
        _tabs.value = remaining
        if (_activeId.value == id) {
            _activeId.value = remaining.lastOrNull()?.id
            _activeId.value?.let { host.showTab(it) }
        }
        return TabOpResult.Ok(tab)
    }

    suspend fun closeOthers(keepId: String, userConfirmed: Boolean): TabOpResult {
        if (!userConfirmed) return TabOpResult.Rejected("Close others cancelled.")
        val keep = _tabs.value.firstOrNull { it.id == keepId }
            ?: return TabOpResult.Rejected("No tab $keepId")
        val ids = TabPolicy.closeOthersIds(_tabs.value, keepId)
        ids.forEach { closeTab(it, userConfirmed = true, closePinnedExplicit = false) }
        val pinnedSkipped = _tabs.value.count { it.pinned && it.id != keepId }
        val warnings = if (pinnedSkipped > 0) {
            listOf("$pinnedSkipped pinned tab(s) were kept. Unpin them to close.")
        } else emptyList()
        emitWarnings(warnings)
        return TabOpResult.Ok(keep, warnings)
    }

    suspend fun select(id: String) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        _activeId.value = id
        val now = clock()
        val next = tab.copy(lastActiveAt = now)
        persist(next)
        store.log(TabLogEvent(id, TabLogEvent.ACTIVE, tab.url, now, null))
        if (tab.liveStatus == TabLiveStatus.LIVE) host.showTab(id)
    }

    suspend fun resume(id: String): TabOpResult {
        val tab = _tabs.value.firstOrNull { it.id == id }
            ?: return TabOpResult.Rejected("No tab $id")
        if (tab.liveStatus == TabLiveStatus.LIVE) {
            select(id)
            return TabOpResult.Ok(tab)
        }
        return when (val hostResult = host.createLiveTab(tab)) {
            is HostResult.Live -> {
                val live = hostResult.tab.copy(
                    liveStatus = TabLiveStatus.LIVE,
                    lastRestoredAt = clock(),
                )
                persist(live)
                store.log(TabLogEvent(id, TabLogEvent.RESUMED, live.url, clock(), null))
                select(id)
                TabOpResult.Ok(live)
            }
            is HostResult.Failed -> TabOpResult.Rejected(
                "Still paused by system: ${hostResult.reason}. The tab record is intact.",
            )
        }
    }

    suspend fun loadUrl(id: String, url: String) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        val normalized = UrlNormalizer.normalize(url)
        host.loadUrl(id, normalized)
        val next = tab.copy(
            url = normalized,
            title = "",
            backStackJson = BackStackCodec.push(tab.backStackJson, normalized),
            lastActiveAt = clock(),
        )
        persist(next)
        store.log(TabLogEvent(id, TabLogEvent.NAVIGATED, normalized, clock(), null))
    }

    fun goBack(id: String) = host.goBack(id)
    fun goForward(id: String) = host.goForward(id)
    fun reload(id: String) = host.reload(id)

    suspend fun setMuted(id: String, muted: Boolean) {
        host.setMuted(id, muted)
        _tabs.value.firstOrNull { it.id == id }?.let { persist(it.copy(muted = muted)) }
    }

    suspend fun setMasterMuted(muted: Boolean) {
        host.setMasterMuted(muted)
        _session.value?.let {
            val next = it.copy(masterMuted = muted)
            _session.value = next
            store.upsertSession(next)
        }
        if (muted) {
            _tabs.value.forEach { host.setMuted(it.id, true) }
        } else {
            _tabs.value.forEach { host.setMuted(it.id, it.muted) }
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        _tabs.value.firstOrNull { it.id == id }?.let { persist(it.copy(pinned = pinned)) }
    }

    suspend fun setLiteMode(id: String, lite: Boolean) {
        host.setLiteMode(id, lite)
        _tabs.value.firstOrNull { it.id == id }?.let { persist(it.copy(liteMode = lite)) }
    }

    suspend fun setKeepVisibleShim(id: String, enabled: Boolean) {
        host.setKeepVisibleShim(id, enabled)
        _tabs.value.firstOrNull { it.id == id }?.let { persist(it.copy(keepVisibleShim = enabled)) }
    }

    suspend fun duplicate(id: String): TabOpResult {
        val tab = _tabs.value.firstOrNull { it.id == id }
            ?: return TabOpResult.Rejected("No tab $id")
        return newTab(duplicateFrom = tab)
    }

    fun dispatchTouch(action: Int, x: Float, y: Float): Boolean {
        val id = _activeId.value ?: return false
        return host.dispatchTouch(id, action, x, y)
    }

    fun injectText(text: String) {
        val id = _activeId.value ?: return
        host.injectText(id, text)
    }

    suspend fun snapshotAll() {
        _tabs.value.forEach { tab ->
            val snap = host.snapshot(tab.id) ?: return@forEach
            persist(snap.copy(id = tab.id, sessionId = tab.sessionId, sortIndex = tab.sortIndex, pinned = tab.pinned, liteMode = tab.liteMode, keepVisibleShim = tab.keepVisibleShim, createdAt = tab.createdAt))
        }
    }

    /**
     * Rebuild every stored tab. Never deletes a record. Parks as
     * PAUSED_BY_SYSTEM when the host/memory cannot take another live WebView.
     */
    suspend fun restoreAll(
        parkPredicate: (restoredLive: Int) -> Boolean,
        reason: String,
    ): RestoreReport {
        attachExisting()
        val ordered = TabPolicy.restoreOrder(_tabs.value)
        val live = mutableListOf<TabRecord>()
        val paused = mutableListOf<TabRecord>()
        val failed = mutableListOf<Pair<TabRecord, String>>()
        val notices = mutableListOf("Restore ($reason): ${ordered.size} tab record(s). None were dropped.")
        val now = clock()
        _session.value?.let {
            val next = it.copy(lastRestoredAt = now, active = true)
            _session.value = next
            store.upsertSession(next)
        }
        for (tab in ordered) {
            if (parkPredicate(live.size)) {
                val parked = tab.copy(liveStatus = TabLiveStatus.PAUSED_BY_SYSTEM, lastRestoredAt = now)
                persist(parked)
                store.log(TabLogEvent(tab.id, TabLogEvent.PAUSED_BY_SYSTEM, tab.url, now, "memory budget during restore"))
                paused += parked
                notices += "“${tab.displayTitle}” is paused by system — tap to resume. Record kept."
                continue
            }
            when (val result = host.createLiveTab(tab)) {
                is HostResult.Live -> {
                    val restored = result.tab.copy(liveStatus = TabLiveStatus.LIVE, lastRestoredAt = now)
                    persist(restored)
                    store.log(TabLogEvent(tab.id, TabLogEvent.RESTORED, restored.url, now, reason))
                    live += restored
                }
                is HostResult.Failed -> {
                    val parked = tab.copy(liveStatus = TabLiveStatus.PAUSED_BY_SYSTEM, lastRestoredAt = now)
                    persist(parked)
                    store.log(TabLogEvent(tab.id, TabLogEvent.PAUSED_BY_SYSTEM, tab.url, now, result.reason))
                    paused += parked
                    failed += parked to result.reason
                    notices += "“${tab.displayTitle}” paused by system (${result.reason}). Record kept."
                }
            }
        }
        _activeId.value = live.firstOrNull()?.id ?: paused.firstOrNull()?.id
        _activeId.value?.let { host.showTab(it) }
        emitWarnings(notices)
        val report = RestoreReport(live, paused, failed, notices)
        check(!report.droppedAny(ordered.map { it.id }.toSet())) {
            "BUG: restore dropped a tab record. This is forbidden."
        }
        return report
    }

    fun activeTab(): TabRecord? = _tabs.value.firstOrNull { it.id == _activeId.value }

    fun updateFromHost(tab: TabRecord) {
        val current = _tabs.value.firstOrNull { it.id == tab.id } ?: return
        val merged = current.copy(
            url = tab.url.ifBlank { current.url },
            title = tab.title.ifBlank { current.title },
            faviconUrl = tab.faviconUrl ?: current.faviconUrl,
            canGoBack = tab.canGoBack,
            canGoForward = tab.canGoForward,
            audioPlaying = tab.audioPlaying,
            wasPlayingMedia = current.wasPlayingMedia || tab.audioPlaying || tab.wasPlayingMedia,
            scrollX = tab.scrollX,
            scrollY = tab.scrollY,
            formStateJson = tab.formStateJson ?: current.formStateJson,
            backStackJson = tab.backStackJson ?: current.backStackJson,
        )
        _tabs.value = _tabs.value.map { if (it.id == merged.id) merged else it }
    }

    suspend fun flushHost(tab: TabRecord) {
        persist(tab)
    }

    private suspend fun persist(tab: TabRecord) {
        store.upsertTab(tab)
        val exists = _tabs.value.any { it.id == tab.id }
        _tabs.value = if (exists) _tabs.value.map { if (it.id == tab.id) tab else it }
        else _tabs.value + tab
    }

    private fun emitWarnings(list: List<String>) {
        if (list.isNotEmpty()) _notices.value = list
    }

    companion object {
        const val DEFAULT_SESSION = "persistent-browser"
    }
}
