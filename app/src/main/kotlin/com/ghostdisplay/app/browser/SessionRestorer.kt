package com.ghostdisplay.app.browser

/**
 * Rebuilds every stored tab after process death, reboot, or first attach.
 * Never deletes a record. Parks extras as PAUSED_BY_SYSTEM when memory is tight.
 */
class SessionRestorer(
    private val tabManager: TabManager,
    private val memory: () -> MemorySnapshot,
) {
    suspend fun restore(reason: String): RestoreReport {
        var parked = false
        return tabManager.restoreAll(
            parkPredicate = { liveSoFar ->
                if (parked) return@restoreAll true
                val snap = memory()
                val should = MemoryBudget.parkNext(snap) && liveSoFar >= 1
                if (should) parked = true
                should
            },
            reason = reason,
        )
    }
}
