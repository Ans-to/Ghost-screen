package com.ghostdisplay.app.browser

import android.app.ActivityManager
import android.content.Context

class MemoryBudgetMonitor(private val context: Context) {
    fun read(tabs: List<TabRecord>): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemoryBudget.snapshot(
            availMemBytes = info.availMem,
            totalMemBytes = info.totalMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            tabs = tabs,
        )
    }

    fun ramGb(): Double {
        val snap = read(emptyList())
        return snap.deviceRamGb
    }
}
