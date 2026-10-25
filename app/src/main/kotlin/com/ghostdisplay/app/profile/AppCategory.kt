package com.ghostdisplay.app.profile

/**
 * Category heuristics used only for default profile flags and caveat text.
 * The launch engine does not branch on these — every package takes the same path.
 */
enum class AppCategory {
    BROWSER,
    VIDEO,
    STREAMING_DRM,
    MUSIC,
    MESSAGING,
    SOCIAL,
    GAME,
    FINANCE,
    SYSTEM,
    OTHER,
}

object AppCategoryClassifier {

    fun classify(
        packageName: String,
        label: String,
        requestedOrientation: Int,
        isSystemApp: Boolean,
    ): AppCategory {
        val pkg = packageName.lowercase()
        val name = label.lowercase()
        if (isSystemApp && looksSystem(pkg, name)) return AppCategory.SYSTEM
        if (looksFinance(pkg, name)) return AppCategory.FINANCE
        if (looksDrm(pkg, name)) return AppCategory.STREAMING_DRM
        if (looksBrowser(pkg, name)) return AppCategory.BROWSER
        if (looksMusic(pkg, name)) return AppCategory.MUSIC
        if (looksVideo(pkg, name)) return AppCategory.VIDEO
        if (looksMessaging(pkg, name)) return AppCategory.MESSAGING
        if (looksSocial(pkg, name)) return AppCategory.SOCIAL
        if (looksGame(pkg, name)) return AppCategory.GAME
        if (isSystemApp) return AppCategory.SYSTEM
        return AppCategory.OTHER
    }

    fun looksBrowser(pkg: String, name: String) =
        pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") ||
            pkg.contains("webview") || name.contains("browser") || name.contains("chrome") ||
            name.contains("firefox") || name.contains("edge")

    fun looksDrm(pkg: String, name: String) =
        pkg.contains("netflix") || pkg.contains("disney") || pkg.contains("primevideo") ||
            pkg.contains("hbo") || pkg.contains("hulu") || name.contains("netflix") ||
            name.contains("disney+") || name.contains("prime video")

    fun looksVideo(pkg: String, name: String) =
        pkg.contains("youtube") || pkg.contains("vimeo") || pkg.contains("twitch") ||
            pkg.contains("video") || pkg.contains("vlc") || name.contains("youtube") ||
            name.contains("twitch") || name.contains("video")

    fun looksMusic(pkg: String, name: String) =
        pkg.contains("spotify") || pkg.contains("music") || pkg.contains("podcast") ||
            pkg.contains("audible") || name.contains("music") || name.contains("podcast") ||
            name.contains("spotify")

    fun looksMessaging(pkg: String, name: String) =
        pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal") ||
            pkg.contains("discord") || pkg.contains("sms") || pkg.contains("messag") ||
            name.contains("whatsapp") || name.contains("telegram") || name.contains("signal") ||
            name.contains("discord") || name.contains("messages")

    fun looksSocial(pkg: String, name: String) =
        pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("tiktok") ||
            pkg.contains("twitter") || pkg.contains("reddit") || pkg.contains("snapchat") ||
            name.contains("instagram") || name.contains("tiktok") || name.contains("facebook") ||
            name == "x" || name.contains("social")

    fun looksGame(pkg: String, name: String) =
        pkg.contains("game") || name.contains("game")

    fun looksFinance(pkg: String, name: String) =
        pkg.contains("bank") || pkg.contains("wallet") || pkg.contains("pay") ||
            pkg.contains("upi") || name.contains("bank") || name.contains("wallet") ||
            name.contains("payment") || name.contains("finance")

    fun looksSystem(pkg: String, name: String) =
        pkg.startsWith("com.android.") || pkg.startsWith("com.google.android.settings") ||
            name == "settings" || name == "phone" || name == "contacts" || name == "dialer"
}
