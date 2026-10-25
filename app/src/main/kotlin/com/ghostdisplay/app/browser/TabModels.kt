package com.ghostdisplay.app.browser

/**
 * Pure tab models — no Android types — so TabManager and unit tests share them.
 */

enum class TabLiveStatus {
    /** WebView is attached, not paused, rendering/media allowed to continue. */
    LIVE,

    /**
     * The ONLY non-live state. The Room record is intact; the user taps to
     * resume. Used exclusively when the OS/memory budget refuses to create
     * another renderer. Never a silent drop.
     */
    PAUSED_BY_SYSTEM,
}

data class TabRecord(
    val id: String,
    val sessionId: String,
    val url: String,
    val title: String,
    val faviconUrl: String?,
    val scrollX: Int,
    val scrollY: Int,
    val formStateJson: String?,
    val backStackJson: String?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val muted: Boolean,
    val pinned: Boolean,
    val liteMode: Boolean,
    val keepVisibleShim: Boolean,
    val wasPlayingMedia: Boolean,
    val audioPlaying: Boolean,
    val createdAt: Long,
    val lastActiveAt: Long,
    val lastRestoredAt: Long?,
    val sortIndex: Int,
    val liveStatus: TabLiveStatus,
) {
    val displayTitle: String
        get() = title.ifBlank { url.ifBlank { "New tab" } }
}

data class TabLogEvent(
    val tabId: String,
    val event: String,
    val url: String,
    val timestamp: Long,
    val detail: String?,
) {
    companion object {
        const val CREATED = "created"
        const val ACTIVE = "active"
        const val RESTORED = "restored"
        const val CLOSED = "closed"
        const val PAUSED_BY_SYSTEM = "paused_by_system"
        const val RESUMED = "resumed"
        const val DUPLICATED = "duplicated"
        const val NAVIGATED = "navigated"
    }
}

data class BrowserSessionState(
    val id: String,
    val active: Boolean,
    val masterMuted: Boolean,
    val maxTabs: Int,
    val createdAt: Long,
    val lastRestoredAt: Long?,
    val displaySessionId: String?,
    val notices: List<String> = emptyList(),
)

sealed class TabOpResult {
    data class Ok(val tab: TabRecord, val warnings: List<String> = emptyList()) : TabOpResult()
    data class Rejected(val reason: String) : TabOpResult()
}

data class RestoreReport(
    val live: List<TabRecord>,
    val pausedBySystem: List<TabRecord>,
    val failed: List<Pair<TabRecord, String>>,
    val notices: List<String>,
) {
    val allRecords: List<TabRecord>
        get() = live + pausedBySystem + failed.map { it.first }

    fun droppedAny(originalIds: Set<String>): Boolean =
        originalIds.any { id -> allRecords.none { it.id == id } }
}
