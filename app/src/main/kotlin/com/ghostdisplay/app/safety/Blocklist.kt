package com.ghostdisplay.app.safety

/**
 * Default finance packages. The engine warns (toggle on by default) rather
 * than hard-stopping; user-added blocklist rows still refuse launch. Matching is package-name and label
 * based so a user-defined extra list can reuse the same rules.
 *
 * A virtual display hides the picture, not the system.
 */
object Blocklist {

    val DEFAULT_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel",
        "com.phonepe.app",
        "net.one97.paytm",
        "com.dreamplug.androidapp", // CRED
        "com.csam.icici.bank.imobile",
        "com.sbi.lotusintouch",
        "com.axis.mobile",
        "com.snapwork.hdfc",
        "com.konylabs.capitalone",
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "com.bofa.bankofamerica",
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.squareup.cash",
        "com.stripe.android.dashboard",
        "com.wise.wisebank",
        "com.revolut.revolut",
        "com.binance.dev",
        "com.coinbase.android",
        "piuk.blockchain.android",
        "com.whatsapp.w4b", // business payments adjacent; user can unblock
    )

    private val LABEL_KEYWORDS = listOf(
        "bank", "banking", "wallet", "payment", "paytm", "phonepe",
        "upi", "credit union", "crypto exchange",
    )

    fun isDefaultBlocked(packageName: String, label: String): Boolean {
        if (packageName in DEFAULT_PACKAGES) return true
        val l = label.lowercase()
        return LABEL_KEYWORDS.any { kw -> l == kw || l.startsWith("$kw ") || l.endsWith(" $kw") || l.contains(" $kw ") }
    }

    fun reason(packageName: String, label: String): String {
        return if (packageName in DEFAULT_PACKAGES) {
            "Hard-blocked: $packageName is a known payments / banking app. A hidden display is not isolation."
        } else {
            "Hard-blocked: “$label” looks like a banking or payments app. Remove it from the blocklist only if you accept the risk."
        }
    }
}
