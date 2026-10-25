package com.ghostdisplay.app.browser

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "browser_sessions")
data class BrowserSessionEntity(
    @PrimaryKey val id: String,
    val active: Boolean,
    val masterMuted: Boolean,
    val maxTabs: Int,
    val createdAt: Long,
    val lastRestoredAt: Long?,
    val displaySessionId: String?,
)

@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val url: String,
    val title: String,
    val faviconUrl: String?,
    val scrollX: Int,
    val scrollY: Int,
    val formStateJson: String?,
    val backStackJson: String?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val muted: Boolean,
    val pinned: Boolean,
    val liteMode: Boolean,
    val keepVisibleShim: Boolean,
    val wasPlayingMedia: Boolean,
    val audioPlaying: Boolean,
    val createdAt: Long,
    val lastActiveAt: Long,
    val lastRestoredAt: Long?,
    val sortIndex: Int,
    val liveStatus: String,
)

@Entity(tableName = "tab_history_log")
data class TabHistoryLogEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val tabId: String,
    val event: String,
    val url: String,
    val timestamp: Long,
    val detail: String?,
)

@Dao
interface BrowserSessionDao {
    @Query("SELECT * FROM browser_sessions WHERE active = 1 LIMIT 1")
    suspend fun active(): BrowserSessionEntity?

    @Query("SELECT * FROM browser_sessions LIMIT 1")
    suspend fun any(): BrowserSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BrowserSessionEntity)
}

@Dao
interface BrowserTabDao {
    @Query("SELECT * FROM browser_tabs ORDER BY sortIndex ASC")
    suspend fun all(): List<BrowserTabEntity>

    @Query("SELECT * FROM browser_tabs ORDER BY sortIndex ASC")
    fun observeAll(): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BrowserTabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BrowserTabEntity)

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TabHistoryDao {
    @Insert
    suspend fun insert(entity: TabHistoryLogEntity)

    @Query("SELECT * FROM tab_history_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<TabHistoryLogEntity>

    @Query("SELECT * FROM tab_history_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<TabHistoryLogEntity>>
}

fun BrowserSessionEntity.toModel() = BrowserSessionState(
    id, active, masterMuted, maxTabs, createdAt, lastRestoredAt, displaySessionId,
)

fun BrowserSessionState.toEntity() = BrowserSessionEntity(
    id, active, masterMuted, maxTabs, createdAt, lastRestoredAt, displaySessionId,
)

fun BrowserTabEntity.toModel() = TabRecord(
    id = id,
    sessionId = sessionId,
    url = url,
    title = title,
    faviconUrl = faviconUrl,
    scrollX = scrollX,
    scrollY = scrollY,
    formStateJson = formStateJson,
    backStackJson = backStackJson,
    canGoBack = canGoBack,
    canGoForward = canGoForward,
    muted = muted,
    pinned = pinned,
    liteMode = liteMode,
    keepVisibleShim = keepVisibleShim,
    wasPlayingMedia = wasPlayingMedia,
    audioPlaying = audioPlaying,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
    lastRestoredAt = lastRestoredAt,
    sortIndex = sortIndex,
    liveStatus = runCatching { TabLiveStatus.valueOf(liveStatus) }.getOrDefault(TabLiveStatus.LIVE),
)

fun TabRecord.toEntity() = BrowserTabEntity(
    id, sessionId, url, title, faviconUrl, scrollX, scrollY, formStateJson, backStackJson,
    canGoBack, canGoForward, muted, pinned, liteMode, keepVisibleShim, wasPlayingMedia,
    audioPlaying, createdAt, lastActiveAt, lastRestoredAt, sortIndex, liveStatus.name,
)

fun TabHistoryLogEntity.toModel() = TabLogEvent(tabId, event, url, timestamp, detail)

fun TabLogEvent.toEntity() = TabHistoryLogEntity(
    tabId = tabId, event = event, url = url, timestamp = timestamp, detail = detail,
)
