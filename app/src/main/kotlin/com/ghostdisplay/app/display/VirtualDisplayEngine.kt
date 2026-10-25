package com.ghostdisplay.app.display

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.ghostdisplay.app.util.VirtualDisplayFlags
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Creates and owns VirtualDisplays. Never throws out to UI callers — every
 * failure is a [CreateResult.Failed] with a human-readable reason.
 *
 * Creation path:
 *  1. IMAGE_READER mode → DisplayManager.createVirtualDisplay
 *  2. MEDIA_PROJECTION mode → MediaProjection.createVirtualDisplay
 *     (required for video / secure / audio capture)
 *
 * Flag bits that the public SDK does not expose (SUPPORTS_TOUCH, OWN_FOCUS)
 * are tried first and dropped on failure. The flags that actually stuck are
 * reported so we never silently degrade.
 */
class VirtualDisplayEngine(
    private val displayManager: DisplayManager,
) {
    data class DisplayHandle(
        val sessionId: String,
        val virtualDisplay: VirtualDisplay,
        val displayId: Int,
        val config: DisplayConfig,
        val flagsUsed: Int,
        val flagsLabel: String,
        val surface: GhostSurface,
        val createdViaMediaProjection: Boolean,
    )

    sealed class CreateResult {
        data class Created(val handle: DisplayHandle) : CreateResult()
        data class Failed(val reason: FailureReason, val message: String, val attempts: List<String>) : CreateResult()
    }

    enum class FailureReason {
        DEVICE_LIMIT,
        INVALID_CONFIG,
        MEDIA_PROJECTION_REQUIRED,
        MEDIA_PROJECTION_INVALID,
        SURFACE_FAILED,
        SECURITY_REJECTED,
        UNKNOWN,
    }

    private val handles = ConcurrentHashMap<String, DisplayHandle>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun get(sessionId: String): DisplayHandle? = handles[sessionId]

    fun all(): List<DisplayHandle> = handles.values.toList()

    fun create(
        config: DisplayConfig,
        mediaProjection: MediaProjection?,
        sessionId: String = UUID.randomUUID().toString(),
    ): CreateResult {
        if (config.widthPx <= 0 || config.heightPx <= 0) {
            return CreateResult.Failed(
                FailureReason.INVALID_CONFIG,
                "Display size ${config.widthPx}x${config.heightPx} is invalid.",
                emptyList(),
            )
        }

        val surface = try {
            GhostSurface(config.widthPx, config.heightPx, config.surfaceMode)
        } catch (t: Throwable) {
            return CreateResult.Failed(
                FailureReason.SURFACE_FAILED,
                "Could not allocate a surface: ${t.message}",
                emptyList(),
            )
        }

        val wantMp = config.surfaceMode == DisplayConfig.SurfaceMode.MEDIA_PROJECTION || config.secure
        if (config.secure && mediaProjection == null) {
            surface.close()
            return CreateResult.Failed(
                FailureReason.MEDIA_PROJECTION_REQUIRED,
                "A secure hidden display needs the standard screen-capture prompt. " +
                    "Nothing is recorded — the consent is only used to create the display.",
                emptyList(),
            )
        }

        val attempts = VirtualDisplayFlags.attempts(secure = config.secure)
        val attemptLog = mutableListOf<String>()

        // App-owned DisplayManager displays report GhostDisplay as ownerUid.
        // MediaProjection displays are often owned by system_server; Xiaomi then
        // rejects startActivity(..., launchDisplayId) for every other package.
        if (!config.secure) {
            val dmResult = createViaDisplayManager(sessionId, config, surface, attempts, attemptLog)
            if (dmResult != null) return dmResult
        }

        if (wantMp && mediaProjection != null) {
            val result = createViaMediaProjection(
                sessionId, config, surface, mediaProjection, attempts, attemptLog,
            )
            if (result != null) return result
        }

        if (config.secure) {
            val dmResult = createViaDisplayManager(sessionId, config, surface, attempts, attemptLog)
            if (dmResult != null) return dmResult
        }

        surface.close()
        return CreateResult.Failed(
            FailureReason.DEVICE_LIMIT,
            "This device refused to create a virtual display. Some manufacturers cap " +
                "the number of virtual displays or block them for third-party apps. " +
                "GhostDisplay cannot run hidden apps here.",
            attemptLog,
        )
    }

    fun setSurface(sessionId: String, surface: Surface?): Boolean {
        val handle = handles[sessionId] ?: return false
        return try {
            handle.virtualDisplay.surface = surface
            true
        } catch (t: Throwable) {
            Log.w(TAG, "setSurface failed", t)
            false
        }
    }

    fun restoreEngineSurface(sessionId: String): Boolean {
        val handle = handles[sessionId] ?: return false
        return try {
            handle.virtualDisplay.surface = handle.surface.surface
            true
        } catch (t: Throwable) {
            Log.w(TAG, "restoreEngineSurface failed", t)
            false
        }
    }

    fun resize(sessionId: String, width: Int, height: Int, dpi: Int): Boolean {
        val handle = handles[sessionId] ?: return false
        return try {
            handle.virtualDisplay.resize(width, height, dpi)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "resize failed", t)
            false
        }
    }

    fun release(sessionId: String) {
        val handle = handles.remove(sessionId) ?: return
        runCatching { handle.virtualDisplay.release() }
        runCatching { handle.surface.close() }
    }

    fun releaseAll() {
        handles.keys.toList().forEach { release(it) }
    }

    private fun createViaMediaProjection(
        sessionId: String,
        config: DisplayConfig,
        surface: GhostSurface,
        mediaProjection: MediaProjection,
        attempts: List<VirtualDisplayFlags.FlagSet>,
        attemptLog: MutableList<String>,
    ): CreateResult? {
        for (flagSet in attempts) {
            try {
                val vd = mediaProjection.createVirtualDisplay(
                    config.name,
                    config.widthPx,
                    config.heightPx,
                    config.densityDpi,
                    flagSet.value,
                    surface.surface,
                    object : VirtualDisplay.Callback() {
                        override fun onStopped() {
                            Log.i(TAG, "VirtualDisplay stopped: $sessionId")
                        }
                    },
                    mainHandler,
                )
                if (vd != null) {
                    attemptLog += "MediaProjection ${flagSet.name}: ok (displayId=${vd.display.displayId})"
                    return success(sessionId, config, surface, vd, flagSet, createdViaMp = true)
                }
                attemptLog += "MediaProjection ${flagSet.name}: returned null"
            } catch (se: SecurityException) {
                attemptLog += "MediaProjection ${flagSet.name}: SecurityException ${se.message}"
            } catch (t: Throwable) {
                attemptLog += "MediaProjection ${flagSet.name}: ${t.javaClass.simpleName} ${t.message}"
            }
        }
        return null
    }

    private fun createViaDisplayManager(
        sessionId: String,
        config: DisplayConfig,
        surface: GhostSurface,
        attempts: List<VirtualDisplayFlags.FlagSet>,
        attemptLog: MutableList<String>,
    ): CreateResult? {
        for (flagSet in attempts) {
            try {
                val vd = displayManager.createVirtualDisplay(
                    config.name,
                    config.widthPx,
                    config.heightPx,
                    config.densityDpi,
                    surface.surface,
                    flagSet.value,
                )
                if (vd != null) {
                    attemptLog += "DisplayManager ${flagSet.name}: ok (displayId=${vd.display.displayId})"
                    return success(sessionId, config, surface, vd, flagSet, createdViaMp = false)
                }
                attemptLog += "DisplayManager ${flagSet.name}: returned null"
            } catch (se: SecurityException) {
                attemptLog += "DisplayManager ${flagSet.name}: SecurityException ${se.message}"
            } catch (t: Throwable) {
                attemptLog += "DisplayManager ${flagSet.name}: ${t.javaClass.simpleName} ${t.message}"
            }
        }
        return null
    }

    private fun success(
        sessionId: String,
        config: DisplayConfig,
        surface: GhostSurface,
        vd: VirtualDisplay,
        flagSet: VirtualDisplayFlags.FlagSet,
        createdViaMp: Boolean,
    ): CreateResult {
        val handle = DisplayHandle(
            sessionId = sessionId,
            virtualDisplay = vd,
            displayId = vd.display.displayId,
            config = config,
            flagsUsed = flagSet.value,
            flagsLabel = flagSet.name,
            surface = surface,
            createdViaMediaProjection = createdViaMp,
        )
        handles[sessionId] = handle
        Log.i(TAG, "Created VD id=${handle.displayId} flags=${flagSet.name} mp=$createdViaMp")
        return CreateResult.Created(handle)
    }

    companion object {
        private const val TAG = "GhostVD"
    }
}
