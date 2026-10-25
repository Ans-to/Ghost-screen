package com.ghostdisplay.app.util

/**
 * Public + hidden VirtualDisplay flag bits from AOSP DisplayManager.
 *
 * SUPPORTS_TOUCH (1<<6) and OWN_FOCUS (1<<11) are @hide but are the flags
 * requested by the GhostDisplay architecture. We pass them as integers so we
 * do not depend on a modified SDK. Creation is always attempted with a
 * fallback ladder; the flags that actually stuck are reported to the user.
 */
object VirtualDisplayFlags {
    const val PUBLIC = 1 shl 0
    const val PRESENTATION = 1 shl 1
    const val SECURE = 1 shl 2
    const val OWN_CONTENT_ONLY = 1 shl 3
    const val AUTO_MIRROR = 1 shl 4
    const val CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 shl 5
    const val SUPPORTS_TOUCH = 1 shl 6
    const val ROTATES_WITH_CONTENT = 1 shl 7
    const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
    const val SHOULD_SHOW_SYSTEM_DECORATIONS = 1 shl 9
    const val TRUSTED = 1 shl 10
    const val OWN_FOCUS = 1 shl 11

    data class FlagSet(
        val name: String,
        val value: Int,
    )

    /**
     * Attempts, richest first. AUTO_MIRROR is intentionally absent — mirroring
     * the physical display is a non-goal. OWN_CONTENT_ONLY is absent because it
     * would prevent launching *other* apps onto the display.
     *
     * TRUSTED / SYSTEM_DECORATIONS almost always require privileged identity,
     * so they are not in the default ladder (trying them first would just fail
     * and confuse the user).
     */
    fun attempts(secure: Boolean): List<FlagSet> {
        val extra = if (secure) SECURE else 0
        return listOf(
            FlagSet(
                "public+presentation+touch+own_focus+destroy",
                PUBLIC or PRESENTATION or SUPPORTS_TOUCH or OWN_FOCUS or
                    DESTROY_CONTENT_ON_REMOVAL or extra,
            ),
            FlagSet(
                "public+presentation+touch+own_focus",
                PUBLIC or PRESENTATION or SUPPORTS_TOUCH or OWN_FOCUS or extra,
            ),
            FlagSet(
                "public+presentation+touch",
                PUBLIC or PRESENTATION or SUPPORTS_TOUCH or extra,
            ),
            FlagSet(
                "public+presentation",
                PUBLIC or PRESENTATION or extra,
            ),
            FlagSet(
                "presentation",
                PRESENTATION or extra,
            ),
            FlagSet(
                "public",
                PUBLIC or extra,
            ),
            FlagSet(
                "none",
                extra,
            ),
        ).distinctBy { it.value }
    }

    fun describe(flags: Int): String {
        if (flags == 0) return "none"
        val names = buildList {
            if (flags and PUBLIC != 0) add("PUBLIC")
            if (flags and PRESENTATION != 0) add("PRESENTATION")
            if (flags and SECURE != 0) add("SECURE")
            if (flags and OWN_CONTENT_ONLY != 0) add("OWN_CONTENT_ONLY")
            if (flags and AUTO_MIRROR != 0) add("AUTO_MIRROR")
            if (flags and SUPPORTS_TOUCH != 0) add("SUPPORTS_TOUCH")
            if (flags and OWN_FOCUS != 0) add("OWN_FOCUS")
            if (flags and DESTROY_CONTENT_ON_REMOVAL != 0) add("DESTROY_CONTENT_ON_REMOVAL")
            if (flags and ROTATES_WITH_CONTENT != 0) add("ROTATES_WITH_CONTENT")
            if (flags and TRUSTED != 0) add("TRUSTED")
            if (flags and SHOULD_SHOW_SYSTEM_DECORATIONS != 0) add("SYSTEM_DECORATIONS")
        }
        return names.joinToString("|")
    }
}
