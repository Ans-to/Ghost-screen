package com.ghostdisplay.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ghostStore by preferencesDataStore(name = "ghost_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val WARNING = booleanPreferencesKey("warning_accepted")
        val RESTORE_BOOT = booleanPreferencesKey("restore_on_boot")
        val RESTORE_DEATH = booleanPreferencesKey("restore_on_process_death")
        val FINANCE_WARN = booleanPreferencesKey("finance_warn")
        val OVERLAY_DOT = booleanPreferencesKey("overlay_dot")
        val SESSION_EXPLAINER = booleanPreferencesKey("session_explainer_seen")
        val OVERLAY_HIDDEN_UNTIL = longPreferencesKey("overlay_hidden_until")
    }

    val onboardingComplete: Flow<Boolean> = context.ghostStore.data.flag(Keys.ONBOARDING)
    val warningAccepted: Flow<Boolean> = context.ghostStore.data.flag(Keys.WARNING)
    val restoreOnBoot: Flow<Boolean> = context.ghostStore.data.flag(Keys.RESTORE_BOOT)
    val restoreOnProcessDeath: Flow<Boolean> = context.ghostStore.data.flag(Keys.RESTORE_DEATH, default = true)
    val financeWarn: Flow<Boolean> = context.ghostStore.data.flag(Keys.FINANCE_WARN, default = true)
    val overlayDot: Flow<Boolean> = context.ghostStore.data.flag(Keys.OVERLAY_DOT, default = false)
    val sessionExplainerSeen: Flow<Boolean> = context.ghostStore.data.flag(Keys.SESSION_EXPLAINER)
    val overlayHiddenUntil: Flow<Long> = context.ghostStore.data.map { it[Keys.OVERLAY_HIDDEN_UNTIL] ?: 0L }

    suspend fun setOnboardingComplete(value: Boolean) = set(Keys.ONBOARDING, value)
    suspend fun setWarningAccepted(value: Boolean) = set(Keys.WARNING, value)
    suspend fun setRestoreOnBoot(value: Boolean) = set(Keys.RESTORE_BOOT, value)
    suspend fun setRestoreOnProcessDeath(value: Boolean) = set(Keys.RESTORE_DEATH, value)
    suspend fun setFinanceWarn(value: Boolean) = set(Keys.FINANCE_WARN, value)
    suspend fun setOverlayDot(value: Boolean) = set(Keys.OVERLAY_DOT, value)
    suspend fun setSessionExplainerSeen(value: Boolean) = set(Keys.SESSION_EXPLAINER, value)
    suspend fun setOverlayHiddenUntil(value: Long) {
        context.ghostStore.edit { it[Keys.OVERLAY_HIDDEN_UNTIL] = value }
    }

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.ghostStore.edit { it[key] = value }
    }

    private fun Flow<Preferences>.flag(
        key: Preferences.Key<Boolean>,
        default: Boolean = false,
    ): Flow<Boolean> = map { it[key] ?: default }
}
