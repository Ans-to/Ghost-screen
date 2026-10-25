package com.ghostdisplay.app.safety

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlocklistTest {

    @Test
    fun knownPaymentPackageIsBlocked() {
        assertThat(Blocklist.isDefaultBlocked("net.one97.paytm", "Paytm")).isTrue()
        assertThat(Blocklist.isDefaultBlocked("com.phonepe.app", "PhonePe")).isTrue()
        assertThat(Blocklist.isDefaultBlocked("com.paypal.android.p2pmobile", "PayPal")).isTrue()
    }

    @Test
    fun labelKeywordBankIsBlocked() {
        assertThat(Blocklist.isDefaultBlocked("com.example.foo", "Example Bank")).isTrue()
    }

    @Test
    fun chromeIsNotBlocked() {
        assertThat(Blocklist.isDefaultBlocked("com.android.chrome", "Chrome")).isFalse()
    }

    @Test
    fun reasonMentionsIsolation() {
        assertThat(Blocklist.reason("net.one97.paytm", "Paytm")).contains("not isolation")
    }
}
