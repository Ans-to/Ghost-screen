package com.ghostdisplay.app.browser

/**
 * Persistence port. Room implements this; tests use an in-memory fake.
 * Deleting a tab record is allowed ONLY after a confirmed user close.
 */
interface TabStore {
    suspend fun session(): BrowserSessionState?
    suspend fun upsertSession(session: BrowserSessionState)
    suspend fun openTabs(): List<TabRecord>
    suspend fun upsertTab(tab: TabRecord)
    suspend fun deleteTab(id: String)
    suspend fun log(event: TabLogEvent)
    suspend fun history(limit: Int = 200): List<TabLogEvent>
}

interface TabEngineHost {
    fun createLiveTab(tab: TabRecord): HostResult
    fun destroyTab(id: String)
    fun loadUrl(id: String, url: String)
    fun goBack(id: String): Boolean
    fun goForward(id: String): Boolean
    fun reload(id: String)
    fun setMuted(id: String, muted: Boolean)
    fun setMasterMuted(muted: Boolean)
    fun setLiteMode(id: String, lite: Boolean)
    fun setKeepVisibleShim(id: String, enabled: Boolean)
    fun showTab(id: String)
    fun captureScrollAndForms(id: String): Pair<Int, Int>?
    fun dispatchTouch(id: String, action: Int, x: Float, y: Float): Boolean
    fun injectText(id: String, text: String)
    fun evaluateMedia(id: String): Boolean
    fun snapshot(id: String): TabRecord?
}

sealed class HostResult {
    data class Live(val tab: TabRecord) : HostResult()
    data class Failed(val reason: String) : HostResult()
}
