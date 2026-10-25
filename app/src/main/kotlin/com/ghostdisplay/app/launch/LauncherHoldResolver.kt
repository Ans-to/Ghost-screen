package com.ghostdisplay.app.launch

import com.ghostdisplay.app.data.LaunchableApp

/**
 * Maps a home-screen long-press to an installed launchable app.
 *
 * Android does not let a third-party app add items to another icon’s
 * launcher menu. After the one-time Accessibility grant, GhostDisplay
 * reads the long-press event from the default home app and opens its
 * own Run hidden / Stop hidden sheet. The engine still treats every
 * package identically — this file only identifies which icon was held.
 */
data class HoldCandidate(
    val eventPackage: String,
    val contentDescription: String?,
    val text: String?,
    val className: String?,
)

object LauncherHoldResolver {

    fun resolve(
        candidate: HoldCandidate,
        launchable: List<LaunchableApp>,
        homePackages: Set<String>,
        selfPackage: String,
    ): LaunchableApp? {
        if (candidate.eventPackage.isBlank()) return null
        if (candidate.eventPackage == selfPackage) return null
        if (candidate.eventPackage !in homePackages) return null
        if (looksLikeWidget(candidate.className)) return null

        val raw = firstLabel(candidate.contentDescription, candidate.text) ?: return null
        if (looksLikeFolder(raw) || looksLikeLauncherChrome(raw)) return null

        matchUnique(raw, launchable)?.let { return it }
        val cleaned = raw.substringBefore(',').substringBefore('·').trim()
        if (cleaned.isNotEmpty() && cleaned != raw) {
            matchUnique(cleaned, launchable)?.let { return it }
        }
        return null
    }

    fun looksLikeFolder(label: String): Boolean {
        val t = label.trim().lowercase()
        return t == "folder" || t.startsWith("folder:") || t.startsWith("folder ")
    }

    fun looksLikeWidget(className: String?): Boolean {
        val c = className?.lowercase() ?: return false
        return c.contains("widget") || c.contains("searchbox") || c.contains("qsb")
    }

    /** Launcher chrome (dock labels, page indicators) — not an app icon. */
    fun looksLikeLauncherChrome(label: String): Boolean {
        val t = label.trim().lowercase()
        return t in LAUNCHER_CHROME || t.endsWith(" page") || t.startsWith("page ")
    }

    private fun firstLabel(vararg values: String?): String? {
        for (v in values) {
            val t = v?.trim().orEmpty()
            if (t.isNotEmpty()) return t
        }
        return null
    }

    private fun matchUnique(label: String, launchable: List<LaunchableApp>): LaunchableApp? {
        val exact = launchable.filter { it.label.equals(label, ignoreCase = true) }
        if (exact.size == 1) return exact[0]
        val pkg = launchable.filter { it.packageName.equals(label, ignoreCase = true) }
        if (pkg.size == 1) return pkg[0]
        return null
    }

    private val LAUNCHER_CHROME = setOf(
        "home", "search", "google", "voice search", "app search",
        "notifications", "uninstall", "widgets", "app info", "edit",
        "remove", "resize",
    )
}
