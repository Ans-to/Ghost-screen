package com.ghostdisplay.app.browser

/**
 * Tiny JSON-ish codec for a list of URLs. Avoids a Gson dependency in pure tests.
 * Format: URLs joined by `\u0001`, which cannot appear in a URL.
 */
object BackStackCodec {
    private const val SEP = '\u0001'

    fun encode(urls: List<String>): String = urls.joinToString(SEP.toString())

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(SEP).filter { it.isNotEmpty() }
    }

    fun push(existing: String?, url: String, max: Int = 50): String {
        val list = decode(existing).toMutableList()
        if (list.lastOrNull() != url) list += url
        return encode(list.takeLast(max))
    }
}
