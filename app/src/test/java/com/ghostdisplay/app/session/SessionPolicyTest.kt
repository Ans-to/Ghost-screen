package com.ghostdisplay.app.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionPolicyTest {

    @Test
    fun processDeathIsResurrectedNeverFresh() {
        assertThat(SessionPolicy.tierAfterGhostProcessDeath()).isEqualTo(SessionTier.RESURRECTED)
        assertThat(SessionPolicy.tierAfterGhostProcessDeath()).isNotEqualTo(SessionTier.FRESH)
    }

    @Test
    fun normalLaunchIsLive() {
        assertThat(SessionPolicy.tierAfterNormalLaunch()).isEqualTo(SessionTier.LIVE)
    }

    @Test
    fun targetCrashIsFresh() {
        assertThat(SessionPolicy.tierAfterTargetCrashOrForceStop()).isEqualTo(SessionTier.FRESH)
    }

    @Test
    fun viewerOpenDoesNotChangeTier() {
        SessionTier.values().forEach { tier ->
            assertThat(SessionPolicy.viewerOpenOrCloseDoesNotChangeTier(tier)).isEqualTo(tier)
        }
    }

    @Test
    fun existingSessionRequiresExplicitChoice() {
        assertThat(SessionPolicy.shouldPromptWhenActiveSessionExists(true)).isTrue()
        assertThat(SessionPolicy.interpretChoice(true, null)).isEqualTo(ExistingSessionChoice.CANCEL)
        assertThat(SessionPolicy.interpretChoice(true, ExistingSessionChoice.RESUME))
            .isEqualTo(ExistingSessionChoice.RESUME)
        assertThat(SessionPolicy.interpretChoice(true, ExistingSessionChoice.ADDITIONAL_WINDOW))
            .isEqualTo(ExistingSessionChoice.ADDITIONAL_WINDOW)
        assertThat(SessionPolicy.interpretChoice(false, null)).isEqualTo(ExistingSessionChoice.RESUME)
    }

    @Test
    fun restoreOutcomeClassification() {
        assertThat(SessionPolicy.classifyRestoreOutcome(true, looksLikeColdStart = true))
            .isEqualTo(RestoreOutcome.FRESH)
        assertThat(SessionPolicy.classifyRestoreOutcome(true, looksLikeColdStart = false))
            .isEqualTo(RestoreOutcome.RESTORED)
        assertThat(SessionPolicy.classifyRestoreOutcome(null, null))
            .isEqualTo(RestoreOutcome.UNKNOWN)
    }

    @Test
    fun stopIsTheOnlyUserEnd() {
        assertThat(SessionPolicy.isStopTheOnlyUserEnd()).isTrue()
    }
}
