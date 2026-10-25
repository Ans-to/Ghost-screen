package com.ghostdisplay.app.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ghostdisplay.app.data.PersistedSession
import com.ghostdisplay.app.data.SessionLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {
    @Insert
    suspend fun insert(entry: SessionLogEntry)

    @Query("SELECT * FROM session_log WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT :limit")
    suspend fun forPackage(pkg: String, limit: Int): List<SessionLogEntry>

    @Query("SELECT * FROM session_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<SessionLogEntry>>
}

class RoomSessionStore(
    private val sessions: com.ghostdisplay.app.data.PersistedSessionDao,
    private val log: SessionLogDao,
) : SessionStore {
    override suspend fun upsertSession(session: PersistedSession) = sessions.upsert(session)
    override suspend fun deactivate(id: String) = sessions.deactivate(id)
    override suspend fun active(): List<PersistedSession> = sessions.active()
    override suspend fun log(entry: SessionLogEntry) = log.insert(entry)
    override suspend fun logsFor(packageName: String, limit: Int) = log.forPackage(packageName, limit)
}
