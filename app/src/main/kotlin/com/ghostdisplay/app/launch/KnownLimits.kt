package com.ghostdisplay.app.launch

/**
 * In-app documentation of known per-app / per-category limits. These are
 * hints shown before launch; they do not replace runtime detection.
 */
object KnownLimits {

    data class Entry(
        val match: (packageName: String, label: String) -> Boolean,
        val category: String,
        val summary: String,
    )

    private val entries = listOf(
        Entry(
            match = { pkg, _ -> pkg in DRM_PACKAGES || pkg.contains("netflix") || pkg.contains("disney") || pkg.contains("primevideo") },
            category = "Streaming (DRM)",
            summary = "Widevine / DRM apps often require a secure hardware surface. Even with MediaProjection + SECURE they may render a black frame. Audio may still play.",
        ),
        Entry(
            match = { pkg, _ -> pkg in setOf("com.google.android.youtube", "com.vanced.android.youtube", "app.revanced.android.youtube") || pkg.contains("youtube") },
            category = "Streaming",
            summary = "YouTube usually renders on a MediaProjection virtual display. Background playback still depends on YouTube's own policy. Use MediaProjection surface mode.",
        ),
        Entry(
            match = { pkg, label -> pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") || label.contains("browser", true) },
            category = "Browser",
            summary = "Browsers are the most reliable hidden-launch targets. Control works when Accessibility can see the virtual-display window.",
        ),
        Entry(
            match = { pkg, _ -> pkg.contains("vlc") || pkg.contains("mxtech") || pkg.contains("gallery") || pkg.contains("videoplayer") },
            category = "Video",
            summary = "Local video players generally work with MediaProjection. Audio continues with the screen off if playback-capture is granted.",
        ),
        Entry(
            match = { _, label -> label.contains("bank", true) || label.contains("wallet", true) || label.contains("paytm", true) },
            category = "Banking",
            summary = "Finance apps warn before start (toggle on by default). A virtual display hides the picture, not the system.",
        ),
        Entry(
            match = { pkg, _ -> pkg.contains("game") || pkg.startsWith("com.supercell") || pkg.startsWith("com.mojang") || pkg.contains("unity") },
            category = "Game",
            summary = "Games often request landscape. GhostDisplay sizes the virtual display accordingly. Many engines ignore virtual displays or need real touch injection, which Android does not grant third-party apps.",
        ),
        Entry(
            match = { pkg, _ -> pkg.contains("instagram") || pkg.contains("whatsapp") || pkg.contains("facebook") || pkg.contains("twitter") || pkg.contains("tiktok") || pkg.contains("snapchat") },
            category = "Social",
            summary = "Social apps usually launch. They will still send notifications and use the network. Enable the notification filter if you want those silenced.",
        ),
    )

    private val DRM_PACKAGES = setOf(
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.amazon.avod.thirdpartyclient",
        "com.amazon.amazonvideo.livingroom",
        "com.hbo.hbonow",
        "com.wbd.stream",
        "com.google.android.videos",
        "com.spotify.tv.android",
    )

    fun forApp(packageName: String, label: String): String? =
        entries.firstOrNull { it.match(packageName, label) }?.summary

    fun categoryOf(packageName: String, label: String): String =
        entries.firstOrNull { it.match(packageName, label) }?.category ?: "Other"

    val matrix: List<Triple<String, String, String>> = listOf(
        Triple("Browser", "Supported / Limited", "Hidden launch is the most likely to succeed. Node-based control typically works."),
        Triple("Video player", "Supported with MediaProjection", "Use MediaProjection surface mode. Audio via playback capture; continues with the screen off."),
        Triple("Streaming (YouTube)", "Limited", "Needs MediaProjection. May pause if the app detects it is not in the foreground."),
        Triple("Streaming (Netflix / DRM)", "Limited / Unsupported", "DRM often refuses a virtual display. May be black video + audio, or fail entirely."),
        Triple("Game (landscape)", "Limited", "Display is sized landscape. Touch injection on virtual displays is not guaranteed; may be view-only."),
        Triple("Social", "Supported / Limited", "Runs hidden; still uses network and notifications unless you enable the filter."),
        Triple("Banking / payments", "Limited (warn)", "Warns before start. A hidden display is not isolation."),
    )
}
