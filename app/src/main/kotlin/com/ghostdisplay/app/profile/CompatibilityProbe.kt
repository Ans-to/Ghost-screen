package com.ghostdisplay.app.profile

import com.ghostdisplay.app.data.Compatibility

data class ProbeResult(
    val compatibility: Compatibility,
    val caveat: String?,
    val category: AppCategory,
    val preLaunchUnsupported: Boolean,
)

/**
 * First-launch probe. Same code path for every package; category only
 * supplies default caveats. Runtime facts always win.
 */
object CompatibilityProbe {

    fun caveatFor(category: AppCategory): String = when (category) {
        AppCategory.BROWSER ->
            "This is your real browser on a live hidden display, not an in-app WebView. Keep the session running and open tabs in the Viewer. GhostDisplay does not inject into the browser’s own tab manager; the live virtual display is what keeps it from being treated as a background app."
        AppCategory.VIDEO ->
            "Video apps usually stay live on a MediaProjection virtual display. Audio can continue with the screen off when capture is granted."
        AppCategory.STREAMING_DRM ->
            "DRM services often refuse a non-secure virtual display. GhostDisplay enables a secure flag when MediaProjection is active. If the app still will not render, it is marked Unsupported — you will see that before relying on a black frame."
        AppCategory.MUSIC ->
            "Music and podcast apps are expected to keep playing with the phone screen off while the hidden session is live."
        AppCategory.MESSAGING ->
            "Open chat, draft text, and in-app navigation stay with the live hidden window. The home-screen icon opens a second window of the same real app on your main screen."
        AppCategory.SOCIAL ->
            "Auto-advancing content (stories, reels, feeds) continues to advance while hidden. That is expected: the hidden window is still the live app."
        AppCategory.GAME ->
            "Games vary. Some run on a virtual display; some detect a non-standard display or missing GPU focus and refuse. Landscape apps keep a landscape virtual display. Marked per session after probe."
        AppCategory.FINANCE ->
            "Finance apps often enforce display-integrity checks and fail on a virtual display. A hidden display is not isolation. The finance warning is on by default."
        AppCategory.SYSTEM ->
            "System apps (Settings, Phone, Contacts) can be started hidden but may have device-wide side effects. Rarely useful."
        AppCategory.OTHER ->
            "This is your real app, same account, on a hidden display. Reopen it here to pick up where you left off."
    }

    fun preLaunch(
        category: AppCategory,
        drmSecureEnabled: Boolean,
    ): ProbeResult {
        val caveat = caveatFor(category)
        val (compat, unsupported) = when (category) {
            AppCategory.STREAMING_DRM ->
                if (drmSecureEnabled) Compatibility.LIMITED to false
                else Compatibility.UNSUPPORTED to true
            AppCategory.FINANCE -> Compatibility.LIMITED to false
            AppCategory.GAME -> Compatibility.LIMITED to false
            AppCategory.SYSTEM -> Compatibility.LIMITED to false
            AppCategory.BROWSER, AppCategory.VIDEO, AppCategory.MUSIC,
            AppCategory.MESSAGING, AppCategory.SOCIAL, AppCategory.OTHER,
            -> Compatibility.SUPPORTED to false
        }
        return ProbeResult(compat, caveat, category, unsupported)
    }

    fun afterLaunch(
        category: AppCategory,
        launchedOnVirtualDisplay: Boolean,
        verifiedOnVirtualDisplay: Boolean?,
        appearedOnDefaultDisplay: Boolean?,
        drmStillRefused: Boolean,
    ): ProbeResult {
        val caveat = caveatFor(category)
        if (appearedOnDefaultDisplay == true || !launchedOnVirtualDisplay) {
            return ProbeResult(
                Compatibility.UNSUPPORTED,
                "This app did not stay on the hidden display. $caveat",
                category,
                true,
            )
        }
        if (category == AppCategory.STREAMING_DRM && drmStillRefused) {
            return ProbeResult(
                Compatibility.UNSUPPORTED,
                "This app refused to render on the virtual display (common for DRM). $caveat",
                category,
                true,
            )
        }
        val compat = when {
            verifiedOnVirtualDisplay == true && category in setOf(
                AppCategory.GAME, AppCategory.FINANCE, AppCategory.SYSTEM, AppCategory.STREAMING_DRM,
            ) -> Compatibility.LIMITED
            verifiedOnVirtualDisplay == true -> Compatibility.SUPPORTED
            verifiedOnVirtualDisplay == null -> Compatibility.LIMITED
            else -> Compatibility.LIMITED
        }
        return ProbeResult(compat, caveat, category, false)
    }
}
