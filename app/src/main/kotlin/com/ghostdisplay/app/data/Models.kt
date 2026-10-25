package com.ghostdisplay.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ghostdisplay.app.display.DisplayConfig

enum class Compatibility { UNKNOWN, SUPPORTED, LIMITED, UNSUPPORTED }

enum class SessionStatus { STARTING, RUNNING, PAUSED, UNSUPPORTED, STOPPED, ERROR }

@Entity(tableName = "app_profiles")
data class AppProfile(
    @PrimaryKey val packageName: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val densityDpi: Int? = null,
    val orientation: String = DisplayConfig.Orientation.AUTO.name,
    val surfaceMode: String = DisplayConfig.SurfaceMode.MEDIA_PROJECTION.name,
    val secure: Boolean = false,
    val audioEnabled: Boolean = true,
    val blockNotifications: Boolean = false,
    val compatibility: String = Compatibility.UNKNOWN.name,
    val lastError: String? = null,
    val lastNoticesJson: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun orientationEnum(): DisplayConfig.Orientation =
        runCatching { DisplayConfig.Orientation.valueOf(orientation) }
            .getOrDefault(DisplayConfig.Orientation.AUTO)

    fun surfaceModeEnum(): DisplayConfig.SurfaceMode =
        runCatching { DisplayConfig.SurfaceMode.valueOf(surfaceMode) }
            .getOrDefault(DisplayConfig.SurfaceMode.IMAGE_READER)

    fun compatibilityEnum(): Compatibility =
        when (compatibility) {
            "WORKS", Compatibility.SUPPORTED.name -> Compatibility.SUPPORTED
            else -> runCatching { Compatibility.valueOf(compatibility) }
                .getOrDefault(Compatibility.UNKNOWN)
        }
}

@Entity(tableName = "persisted_sessions")
data class PersistedSession(
    @PrimaryKey val id: String,
    val packageName: String,
    val appLabel: String,
    val configJson: String,
    val active: Boolean,
    val startedAt: Long,
    val lastDisplayId: Int? = null,
    val activityName: String = "",
    val tier: String = "LIVE",
    val lastViewedAt: Long? = null,
    val wasPlayingMedia: Boolean = false,
    val restoreOutcome: String? = null,
    val orientation: String? = null,
    val surfaceMode: String? = null,
)

@Entity(tableName = "session_log")
data class SessionLogEntry(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val sessionId: String,
    val packageName: String,
    val event: String,
    val timestamp: Long,
    val detail: String?,
)

@Entity(tableName = "blocklist")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val reason: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false,
)

data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val requestedOrientation: Int,
    val resizeable: Boolean,
)

data class CapabilityReport(
    val virtualDisplayCreated: Boolean,
    val flagsUsed: String,
    val flagsRequested: String,
    val launchedOnVirtualDisplay: Boolean,
    val launchStepSucceeded: Int?,
    val verifiedOnVirtualDisplay: Boolean?,
    val nodeControlAvailable: Boolean,
    val gestureControlAvailable: Boolean,
    val audioCaptureAvailable: Boolean,
    val viewOnly: Boolean,
    val notices: List<String>,
    val compatibility: Compatibility,
) {
    companion object {
        fun failed(reason: String): CapabilityReport = CapabilityReport(
            virtualDisplayCreated = false,
            flagsUsed = "none",
            flagsRequested = "none",
            launchedOnVirtualDisplay = false,
            launchStepSucceeded = null,
            verifiedOnVirtualDisplay = false,
            nodeControlAvailable = false,
            gestureControlAvailable = false,
            audioCaptureAvailable = false,
            viewOnly = true,
            notices = listOf(reason),
            compatibility = Compatibility.UNSUPPORTED,
        )
    }
}

data class GhostSession(
    val id: String,
    val packageName: String,
    val activityName: String,
    val appLabel: String,
    val displayId: Int,
    val taskId: Int?,
    val status: SessionStatus,
    val startedAt: Long,
    val config: DisplayConfig,
    val capability: CapabilityReport,
    val muted: Boolean = false,
    val tier: String = "LIVE",
    val lastViewedAt: Long? = null,
    val wasPlayingMedia: Boolean = false,
    val restoreOutcome: String? = null,
    val categoryCaveat: String? = null,
)
