package com.ghostdisplay.app.control

/**
 * Best-effort parser for the optional "task mode" box. Not an agent — it
 * turns a short English phrase into a list of node actions.
 *
 * Examples:
 *  - "open site example.com and click play"
 *  - "type hello world"
 *  - "click Login"
 *  - "scroll down"
 */
object TaskModeParser {

    data class Step(
        val kind: Kind,
        val argument: String,
        val description: String,
    )

    enum class Kind { CLICK_TEXT, SET_TEXT, SCROLL_FORWARD, SCROLL_BACKWARD, OPEN_URL_HINT }

    fun parse(raw: String): List<Step> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val lower = text.lowercase()
        val steps = mutableListOf<Step>()

        extractUrl(text)?.let { url ->
            steps += Step(
                Kind.OPEN_URL_HINT,
                url,
                "Focus an address bar if one exists, then type $url",
            )
            steps += Step(Kind.SET_TEXT, url, "Type $url into the focused field")
        }

        val clickTarget = extractAfter(lower, text, listOf("click ", "tap ", "press "))
        if (clickTarget != null) {
            steps += Step(Kind.CLICK_TEXT, clickTarget, "Click a node whose text is \"$clickTarget\"")
        }

        val typeTarget = extractAfter(lower, text, listOf("type ", "enter ", "search "))
        if (typeTarget != null && steps.none { it.kind == Kind.SET_TEXT && it.argument == typeTarget }) {
            steps += Step(Kind.SET_TEXT, typeTarget, "Type \"$typeTarget\" into a focused or editable field")
        }

        when {
            lower.contains("scroll down") || lower.contains("swipe up") ->
                steps += Step(Kind.SCROLL_FORWARD, "", "Scroll forward")
            lower.contains("scroll up") || lower.contains("swipe down") ->
                steps += Step(Kind.SCROLL_BACKWARD, "", "Scroll backward")
        }

        if (steps.isEmpty()) {
            steps += Step(Kind.CLICK_TEXT, text, "Click a node matching \"$text\"")
        }
        return steps
    }

    private fun extractUrl(text: String): String? {
        val regex = Regex("""(?i)\b((?:https?://)?(?:www\.)?[a-z0-9.-]+\.[a-z]{2,}(?:/[^\s]*)?)""")
        val tagged = Regex("""(?i)(?:open\s+)?(?:site|url)\s+([^\s]+)""").find(text)
        if (tagged != null) return tagged.groupValues[1].trim('"', '\'', '.')
        return regex.find(text)?.groupValues?.get(1)
    }

    private fun extractAfter(lower: String, original: String, prefixes: List<String>): String? {
        for (p in prefixes) {
            val idx = lower.indexOf(p)
            if (idx >= 0) {
                val rest = original.substring(idx + p.length).trim()
                val cut = rest.split(" and ", " then ", ",", limit = 2).first()
                val cleaned = cut.trim().trim('"', '\'')
                if (cleaned.isNotEmpty()) return cleaned
            }
        }
        return null
    }
}
