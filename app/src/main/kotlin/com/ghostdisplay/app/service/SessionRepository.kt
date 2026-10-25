package com.ghostdisplay.app.service

import com.ghostdisplay.app.data.GhostSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory live sessions. The Room [com.ghostdisplay.app.data.PersistedSession]
 * table is a separate durability layer used for opt-in restore.
 */
object SessionRepository {
    private val _sessions = MutableStateFlow<List<GhostSession>>(emptyList())
    val sessions: StateFlow<List<GhostSession>> = _sessions.asStateFlow()

    fun upsert(session: GhostSession) {
        val without = _sessions.value.filter { it.id != session.id }
        _sessions.value = without + session
    }

    fun remove(id: String) {
        _sessions.value = _sessions.value.filter { it.id != id }
    }

    fun clear() {
        _sessions.value = emptyList()
    }

    fun get(id: String): GhostSession? = _sessions.value.firstOrNull { it.id == id }

    fun all(): List<GhostSession> = _sessions.value
}
