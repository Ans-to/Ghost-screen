package com.ghostdisplay.app.browser

class InMemoryTabStore : TabStore {
    var sessionState: BrowserSessionState? = null
    val tabs = LinkedHashMap<String, TabRecord>()
    val logs = mutableListOf<TabLogEvent>()

    override suspend fun session(): BrowserSessionState? = sessionState
    override suspend fun upsertSession(session: BrowserSessionState) {
        sessionState = session
    }
    override suspend fun openTabs(): List<TabRecord> = tabs.values.toList()
    override suspend fun upsertTab(tab: TabRecord) {
        tabs[tab.id] = tab
    }
    override suspend fun deleteTab(id: String) {
        tabs.remove(id)
    }
    override suspend fun log(event: TabLogEvent) {
        logs += event
    }
    override suspend fun history(limit: Int): List<TabLogEvent> = logs.takeLast(limit).reversed()
}

class FakeHost : TabEngineHost {
    val live = LinkedHashMap<String, TabRecord>()
    val destroyed = mutableListOf<String>()
    var failNext: Boolean = false
    var failAlways: Boolean = false
    var touches = 0

    override fun createLiveTab(tab: TabRecord): HostResult {
        if (failAlways || failNext) {
            failNext = false
            return HostResult.Failed("simulated OOM")
        }
        val liveTab = tab.copy(liveStatus = TabLiveStatus.LIVE)
        live[tab.id] = liveTab
        return HostResult.Live(liveTab)
    }

    override fun destroyTab(id: String) {
        live.remove(id)
        destroyed += id
    }

    override fun loadUrl(id: String, url: String) {
        live[id] = live[id]?.copy(url = url) ?: return
    }

    override fun goBack(id: String) = live[id]?.canGoBack == true
    override fun goForward(id: String) = live[id]?.canGoForward == true
    override fun reload(id: String) = Unit
    override fun setMuted(id: String, muted: Boolean) {
        live[id] = live[id]?.copy(muted = muted) ?: return
    }
    override fun setMasterMuted(muted: Boolean) = Unit
    override fun setLiteMode(id: String, lite: Boolean) {
        live[id] = live[id]?.copy(liteMode = lite) ?: return
    }
    override fun setKeepVisibleShim(id: String, enabled: Boolean) {
        live[id] = live[id]?.copy(keepVisibleShim = enabled) ?: return
    }
    override fun showTab(id: String) = Unit
    override fun captureScrollAndForms(id: String) = 0 to 0
    override fun dispatchTouch(id: String, action: Int, x: Float, y: Float): Boolean {
        touches++
        return live.containsKey(id)
    }
    override fun injectText(id: String, text: String) = Unit
    override fun evaluateMedia(id: String) = live[id]?.audioPlaying == true
    override fun snapshot(id: String) = live[id]
}
