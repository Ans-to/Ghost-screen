package com.ghostdisplay.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VirtualDisplayFlagsTest {

    @Test
    fun attemptsNeverIncludeAutoMirror() {
        VirtualDisplayFlags.attempts(secure = false).forEach {
            assertThat(it.value and VirtualDisplayFlags.AUTO_MIRROR).isEqualTo(0)
        }
        VirtualDisplayFlags.attempts(secure = true).forEach {
            assertThat(it.value and VirtualDisplayFlags.AUTO_MIRROR).isEqualTo(0)
        }
    }

    @Test
    fun secureAddsSecureBitToEveryAttempt() {
        VirtualDisplayFlags.attempts(secure = true).forEach {
            assertThat(it.value and VirtualDisplayFlags.SECURE).isEqualTo(VirtualDisplayFlags.SECURE)
        }
    }

    @Test
    fun firstAttemptHasTouchAndOwnFocus() {
        val first = VirtualDisplayFlags.attempts(false).first()
        assertThat(first.value and VirtualDisplayFlags.SUPPORTS_TOUCH).isNotEqualTo(0)
        assertThat(first.value and VirtualDisplayFlags.OWN_FOCUS).isNotEqualTo(0)
        assertThat(first.value and VirtualDisplayFlags.PUBLIC).isNotEqualTo(0)
    }

    @Test
    fun describeListsNames() {
        val text = VirtualDisplayFlags.describe(
            VirtualDisplayFlags.PUBLIC or VirtualDisplayFlags.SECURE,
        )
        assertThat(text).contains("PUBLIC")
        assertThat(text).contains("SECURE")
    }
}
