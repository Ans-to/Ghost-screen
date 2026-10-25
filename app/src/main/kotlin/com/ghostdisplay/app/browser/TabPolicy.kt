package com.ghostdisplay.app.browser

/**
 * The persistence contract, as executable rules.
 *
 * Tabs are never evicted by GhostDisplay. The only ways a tab leaves the
 * open set are: the user confirmed a close, or (records only) the user
 * confirmed "close others". Process death restores records; it does not
 * delete them.
 */
object TabPolicy {
    const val DEFAULT_MAX_TABS = 10
    const val HARD_MAX_TABS = 16
    /** Creating a tab when [openCount] is already >= this produces a RAM warning. */
    const val WARN_AFTER_COUNT = 8

    const val EST_LIVE_BYTES = 150L * 1024 * 1024
    const val EST_LITE_BYTES = 80L * 1024 * 1024
    const val EST_PAUSED_BYTES = 8L * 1024 * 1024

    sealed class NewTabDecision {
        data class Allow(val warnings: List<String>) : NewTabDecision()
        data class Reject(val reason: String) : NewTabDecision()
    }

    fun clampMaxTabs(requested: Int): Int = requested.coerceIn(1, HARD_MAX_TABS)

    fun decideNewTab(
        openCount: Int,
        maxTabs: Int,
        deviceRamGb: Double,
    ): NewTabDecision {
        val cap = clampMaxTabs(maxTabs)
        if (openCount >= HARD_MAX_TABS) {
            return NewTabDecision.Reject(
                "Hard cap is $HARD_MAX_TABS tabs. Close a tab first. GhostDisplay will not silently drop another tab to make room.",
            )
        }
        if (openCount >= cap) {
            return NewTabDecision.Reject(
                "Max tabs is set to $cap. Raise the cap in Browser settings (up to $HARD_MAX_TABS) or close a tab. Nothing was discarded.",
            )
        }
        val warnings = mutableListOf<String>()
        if (openCount >= WARN_AFTER_COUNT) {
            warnings += "You already have $openCount open tabs. Devices with under 4 GB RAM often struggle past 8, especially with video. Tabs will not be auto-closed; the OS may kill the process, in which case every tab is restored from disk."
        }
        if (openCount + 1 >= 11) {
            warnings += "Tab ${openCount + 1} is above the default pool of 10. Allowed, but memory pressure is likely."
        }
        if (deviceRamGb > 0 && deviceRamGb < 4.0 && openCount >= WARN_AFTER_COUNT) {
            warnings += "This device reports ${"%.1f".format(deviceRamGb)} GB RAM. 10+ heavy tabs may cause an OS process kill. Playback position can reset on restore; the tab list will not."
        }
        return NewTabDecision.Allow(warnings)
    }

    /**
     * Close requires an explicit user confirmation. Pinned tabs additionally
     * require [closePinnedExplicit] so a stray confirm on "close others"
     * cannot unpin-kill them.
     */
    fun canClose(
        pinned: Boolean,
        userConfirmed: Boolean,
        closePinnedExplicit: Boolean,
    ): Boolean {
        if (!userConfirmed) return false
        if (pinned && !closePinnedExplicit) return false
        return true
    }

    /** Pinned first, then most recently active, then sortIndex. Never drops ids. */
    fun restoreOrder(tabs: List<TabRecord>): List<TabRecord> {
        return tabs.sortedWith(
            compareByDescending<TabRecord> { it.pinned }
                .thenByDescending { it.lastActiveAt }
                .thenBy { it.sortIndex },
        )
    }

    fun closeOthersIds(tabs: List<TabRecord>, keepId: String): List<String> {
        return tabs.filter { it.id != keepId && !it.pinned }.map { it.id }
    }

    fun estimateBytes(liveHeavy: Int, liveLite: Int, paused: Int): Long {
        return liveHeavy * EST_LIVE_BYTES + liveLite * EST_LITE_BYTES + paused * EST_PAUSED_BYTES
    }

    fun shouldParkRestores(lowMemory: Boolean, availMemBytes: Long, thresholdBytes: Long): Boolean {
        if (lowMemory) return true
        if (thresholdBytes > 0 && availMemBytes in 1 until thresholdBytes) return true
        return false
    }

    fun memoryBarFraction(used: Long, total: Long): Float {
        if (total <= 0L) return 0f
        return (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}
