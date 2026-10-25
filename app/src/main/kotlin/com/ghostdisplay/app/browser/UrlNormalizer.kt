package com.ghostdisplay.app.browser

object UrlNormalizer {
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "about:blank"
        if (trimmed.equals("about:blank", ignoreCase = true)) return "about:blank"
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("content:")) {
            return "about:blank"
        }
        if (SCHEME.containsMatchIn(trimmed)) return trimmed
        if (looksLikeDomain(trimmed)) return "https://$trimmed"
        val q = java.net.URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        return "https://www.google.com/search?q=$q"
    }

    fun looksLikeDomain(value: String): Boolean {
        if (value.contains(' ')) return false
        val host = value.substringBefore('/').substringBefore('?')
        return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
    }

    fun hostOf(url: String): String {
        return try {
            val withScheme = if (SCHEME.containsMatchIn(url)) url else "https://$url"
            java.net.URI(withScheme).host ?: url
        } catch (_: Exception) {
            url
        }
    }
}
