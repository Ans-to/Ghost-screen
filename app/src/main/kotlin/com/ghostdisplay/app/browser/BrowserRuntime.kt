package com.ghostdisplay.app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide handle so ViewerActivity and the main UI can talk to the
 * WebView pool owned by [com.ghostdisplay.app.service.GhostService].
 */
object BrowserRuntime {
    @Volatile var tabManager: TabManager? = null
    @Volatile var host: PersistentWebViewHost? = null
    @Volatile var restorer: SessionRestorer? = null
    @Volatile var memory: MemoryBudgetMonitor? = null
    @Volatile var displaySessionId: String? = null
    @Volatile var running: Boolean = false
    @Volatile var presentationNotice: String? = null

    private val _restore = MutableStateFlow<RestoreReport?>(null)
    val restore: StateFlow<RestoreReport?> = _restore.asStateFlow()

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    fun publishRestore(report: RestoreReport?) {
        _restore.value = report
    }

    fun pulse() {
        _tick.value = System.currentTimeMillis()
    }

    fun reset() {
        tabManager = null
        host = null
        restorer = null
        memory = null
        displaySessionId = null
        running = false
        presentationNotice = null
        _restore.value = null
        pulse()
    }
}
