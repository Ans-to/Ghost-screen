package com.ghostdisplay.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ghostdisplay.app.browser.BrowserSessionDao
import com.ghostdisplay.app.browser.BrowserSessionEntity
import com.ghostdisplay.app.browser.BrowserTabDao
import com.ghostdisplay.app.browser.BrowserTabEntity
import com.ghostdisplay.app.browser.TabHistoryDao
import com.ghostdisplay.app.browser.TabHistoryLogEntity
import com.ghostdisplay.app.session.SessionLogDao
import kotlinx.coroutines.flow.Flow

@Dao
interface AppProfileDao {
    @Query("SELECT * FROM app_profiles")
    fun observeAll(): Flow<List<AppProfile>>

    @Query("SELECT * FROM app_profiles WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): AppProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: AppProfile)

    @Query("UPDATE app_profiles SET compatibility = :compat, lastError = :error, lastNoticesJson = :notices, updatedAt = :now WHERE packageName = :pkg")
    suspend fun updateCompatibility(pkg: String, compat: String, error: String?, notices: String?, now: Long)
}

@Dao
interface PersistedSessionDao {
    @Query("SELECT * FROM persisted_sessions WHERE active = 1")
    suspend fun active(): List<PersistedSession>

    @Query("SELECT * FROM persisted_sessions WHERE active = 1")
    fun observeActive(): Flow<List<PersistedSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: PersistedSession)

    @Query("UPDATE persisted_sessions SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)

    @Query("UPDATE persisted_sessions SET active = 0")
    suspend fun deactivateAll()
}

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM blocklist")
    fun observeAll(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocklist")
    suspend fun all(): List<BlockedApp>

    @Query("SELECT * FROM blocklist WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): BlockedApp?

    @Query("SELECT COUNT(*) FROM blocklist WHERE packageName = :pkg")
    suspend fun isBlocked(pkg: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: BlockedApp)

    @Query("DELETE FROM blocklist WHERE packageName = :pkg AND isDefault = 0")
    suspend fun removeUser(pkg: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS browser_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                active INTEGER NOT NULL,
                masterMuted INTEGER NOT NULL,
                maxTabs INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                lastRestoredAt INTEGER,
                displaySessionId TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS browser_tabs (
                id TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT NOT NULL,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                faviconUrl TEXT,
                scrollX INTEGER NOT NULL,
                scrollY INTEGER NOT NULL,
                formStateJson TEXT,
                backStackJson TEXT,
                canGoBack INTEGER NOT NULL,
                canGoForward INTEGER NOT NULL,
                muted INTEGER NOT NULL,
                pinned INTEGER NOT NULL,
                liteMode INTEGER NOT NULL,
                keepVisibleShim INTEGER NOT NULL,
                wasPlayingMedia INTEGER NOT NULL,
                audioPlaying INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                lastActiveAt INTEGER NOT NULL,
                lastRestoredAt INTEGER,
                sortIndex INTEGER NOT NULL,
                liveStatus TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tab_history_log (
                rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                tabId TEXT NOT NULL,
                event TEXT NOT NULL,
                url TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                detail TEXT
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE persisted_sessions ADD COLUMN activityName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE persisted_sessions ADD COLUMN tier TEXT NOT NULL DEFAULT 'LIVE'")
        db.execSQL("ALTER TABLE persisted_sessions ADD COLUMN lastViewedAt INTEGER")
        db.execSQL("ALTER TABLE persisted_sessions ADD COLUMN wasPlayingMedia INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE persisted_sessions ADD COLUMN restoreOutcome TEXT")
        db.execSQL("ALTER TABLE persisted_sessions ADD COLUMN orientation TEXT")
        db.execSQL("ALTER TABLE persisted_sessions ADD COLUMN surfaceMode TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS session_log (
                rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId TEXT NOT NULL,
                packageName TEXT NOT NULL,
                event TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                detail TEXT
            )
            """.trimIndent(),
        )
    }
}

@Database(
    entities = [
        AppProfile::class,
        PersistedSession::class,
        BlockedApp::class,
        BrowserSessionEntity::class,
        BrowserTabEntity::class,
        TabHistoryLogEntity::class,
        SessionLogEntry::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class GhostDatabase : RoomDatabase() {
    abstract fun profiles(): AppProfileDao
    abstract fun sessions(): PersistedSessionDao
    abstract fun blocklist(): BlocklistDao
    abstract fun browserSessions(): BrowserSessionDao
    abstract fun browserTabs(): BrowserTabDao
    abstract fun tabHistory(): TabHistoryDao
    abstract fun sessionLog(): SessionLogDao

    companion object {
        fun create(context: Context): GhostDatabase =
            Room.databaseBuilder(context, GhostDatabase::class.java, "ghostdisplay.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
