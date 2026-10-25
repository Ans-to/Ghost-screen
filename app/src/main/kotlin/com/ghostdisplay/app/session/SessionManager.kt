package com.ghostdisplay.app.session

import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.PersistedSession
import com.ghostdisplay.app.data.SessionLogEntry
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.service.SessionRepository
import com.google.gson.Gson

/**
 * Owns hidden-window lifecycle and tiers for every installed app.
 * Never ends a session on Viewer close, screen lock, or memory pressure.
 */
class SessionManager(
    private val store: SessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val gson: Gson = Gson(),
) {
    fun liveForPackage(packageName: String): GhostSession? =
        SessionRepository.all().firstOrNull {
            it.packageName == packageName && it.status == SessionStatus.RUNNING
        }

    fun markViewed(sessionId: String) {
        val now = clock()
        SessionRepository.get(sessionId)?.let {
            val next = it.copy(
                lastViewedAt = now,
                tier = SessionPolicy.viewerOpenOrCloseDoesNotChangeTier(
                    runCatching { SessionTier.valueOf(it.tier) }.getOrDefault(SessionTier.LIVE),
                ).name,
            )
            SessionRepository.upsert(next)
        }
    }

    fun markTier(sessionId: String, tier: SessionTier, outcome: RestoreOutcome? = null) {
        SessionRepository.get(sessionId)?.let {
            SessionRepository.upsert(
                it.copy(tier = tier.name, restoreOutcome = outcome?.name ?: it.restoreOutcome),
            )
        }
    }

    suspend fun persist(session: GhostSession, active: Boolean, logEvent: String? = if (active) EVENT_CREATED else EVENT_STOPPED) {
        store.upsertSession(
            PersistedSession(
                id = session.id,
                packageName = session.packageName,
                appLabel = session.appLabel,
                configJson = gson.toJson(session.config),
                active = active,
                startedAt = session.startedAt,
                lastDisplayId = session.displayId,
                activityName = session.activityName,
                tier = session.tier,
                lastViewedAt = session.lastViewedAt,
                wasPlayingMedia = session.wasPlayingMedia,
                restoreOutcome = session.restoreOutcome,
                orientation = session.config.orientation.name,
                surfaceMode = session.config.surfaceMode.name,
            ),
        )
        if (logEvent != null) {
            store.log(
                SessionLogEntry(
                    sessionId = session.id,
                    packageName = session.packageName,
                    event = logEvent,
                    timestamp = clock(),
                    detail = session.tier,
                ),
            )
        }
    }

    suspend fun log(sessionId: String, packageName: String, event: String, detail: String?) {
        store.log(SessionLogEntry(sessionId = sessionId, packageName = packageName, event = event, timestamp = clock(), detail = detail))
    }

    suspend fun deactivate(sessionId: String) {
        store.deactivate(sessionId)
        val s = SessionRepository.get(sessionId)
        if (s != null) {
            store.log(
                SessionLogEntry(
                    sessionId = sessionId,
                    packageName = s.packageName,
                    event = "stopped",
                    timestamp = clock(),
                    detail = s.tier,
                ),
            )
        }
    }

    companion object {
        const val EVENT_CREATED = "created"
        const val EVENT_VIEWED = "viewed"
        const val EVENT_TIER = "tier"
        const val EVENT_STOPPED = "stopped"
        const val EVENT_RESURRECTED = "resurrected"
        const val EVENT_FRESH = "fresh"
    }
}

interface SessionStore {
    suspend fun upsertSession(session: PersistedSession)
    suspend fun deactivate(id: String)
    suspend fun active(): List<PersistedSession>
    suspend fun log(entry: SessionLogEntry)
    suspend fun logsFor(packageName: String, limit: Int = 50): List<SessionLogEntry>
}
