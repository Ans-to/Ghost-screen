package com.ghostdisplay.app.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppCategoryTest {

    @Test
    fun financeFromPackageOrLabel() {
        assertThat(AppCategoryClassifier.classify("com.example.wallet", "Wallet", -1, false))
            .isEqualTo(AppCategory.FINANCE)
        assertThat(AppCategoryClassifier.classify("com.foo.bar", "City Bank", -1, false))
            .isEqualTo(AppCategory.FINANCE)
    }

    @Test
    fun gameUsesKeywordNotOrientation() {
        assertThat(AppCategoryClassifier.classify("com.foo.bar", "Maps", 0, false))
            .isEqualTo(AppCategory.OTHER)
        assertThat(AppCategoryClassifier.classify("com.foo.game", "Puzzle", 0, false))
            .isEqualTo(AppCategory.GAME)
    }

    @Test
    fun socialMessagingBrowserAreHeuristicOnly() {
        assertThat(AppCategoryClassifier.classify("com.instagram.android", "Instagram", -1, false))
            .isEqualTo(AppCategory.SOCIAL)
        assertThat(AppCategoryClassifier.classify("com.whatsapp", "WhatsApp", -1, false))
            .isEqualTo(AppCategory.MESSAGING)
        assertThat(AppCategoryClassifier.classify("com.android.chrome", "Chrome", -1, false))
            .isEqualTo(AppCategory.BROWSER)
    }
}
