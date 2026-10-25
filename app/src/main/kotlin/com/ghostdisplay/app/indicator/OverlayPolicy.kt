package com.ghostdisplay.app.indicator

/**
 * Pure rules for the 8dp overlay dot. Never shown over home, over GhostDisplay,
 * over the Viewer, or over a different app than the hidden session.
 */
object OverlayPolicy {

    data class Input(
        val overlayOptIn: Boolean,
        val overlayGranted: Boolean,
        val hiddenUntilElapsed: Boolean,
        val viewerOpen: Boolean,
        val foregroundPackage: String?,
        val hiddenSessionPackage: String?,
        val isHomeOrLauncher: Boolean,
        val isGhostDisplay: Boolean,
    )

    fun shouldShow(input: Input): Boolean {
        if (!input.overlayOptIn) return false
        if (!input.overlayGranted) return false
        if (!input.hiddenUntilElapsed) return false
        if (input.viewerOpen) return false
        val fg = input.foregroundPackage ?: return false
        val hidden = input.hiddenSessionPackage ?: return false
        if (input.isHomeOrLauncher) return false
        if (input.isGhostDisplay) return false
        return fg == hidden
    }

    fun endOfDayMillis(now: Long, dayStartHour: Int = 0): Long {
        val day = 24L * 60 * 60 * 1000
        val offset = (now + java.util.TimeZone.getDefault().getOffset(now) - dayStartHour * 3600000L)
        val start = offset - (offset % day)
        return start + day - java.util.TimeZone.getDefault().getOffset(now)
    }
}
