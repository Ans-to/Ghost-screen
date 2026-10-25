package com.ghostdisplay.app.browser

data class MemorySnapshot(
    val availMemBytes: Long,
    val totalMemBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    val estimatedUsedBytes: Long,
    val liveTabs: Int,
    val pausedTabs: Int,
    val deviceRamGb: Double,
) {
    val fraction: Float get() = TabPolicy.memoryBarFraction(estimatedUsedBytes, totalMemBytes)
    val label: String
        get() {
            fun mb(v: Long) = (v / (1024.0 * 1024.0)).toInt()
            val state = if (lowMemory) "LOW MEMORY" else "ok"
            return "${mb(estimatedUsedBytes)} MB estimated · ${mb(availMemBytes)} MB free · $state"
        }
}

object MemoryBudget {
    fun snapshot(
        availMemBytes: Long,
        totalMemBytes: Long,
        thresholdBytes: Long,
        lowMemory: Boolean,
        tabs: List<TabRecord>,
    ): MemorySnapshot {
        val liveLite = tabs.count { it.liveStatus == TabLiveStatus.LIVE && it.liteMode }
        val liveHeavy = tabs.count { it.liveStatus == TabLiveStatus.LIVE && !it.liteMode }
        val paused = tabs.count { it.liveStatus == TabLiveStatus.PAUSED_BY_SYSTEM }
        val used = TabPolicy.estimateBytes(liveHeavy, liveLite, paused)
        val ramGb = if (totalMemBytes > 0) totalMemBytes / (1024.0 * 1024.0 * 1024.0) else 0.0
        return MemorySnapshot(
            availMemBytes = availMemBytes,
            totalMemBytes = totalMemBytes,
            thresholdBytes = thresholdBytes,
            lowMemory = lowMemory,
            estimatedUsedBytes = used,
            liveTabs = liveHeavy + liveLite,
            pausedTabs = paused,
            deviceRamGb = ramGb,
        )
    }

    fun parkNext(snapshot: MemorySnapshot): Boolean =
        TabPolicy.shouldParkRestores(snapshot.lowMemory, snapshot.availMemBytes, snapshot.thresholdBytes)
}
