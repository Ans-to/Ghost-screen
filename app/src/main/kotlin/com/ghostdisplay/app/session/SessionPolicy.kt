package com.ghostdisplay.app.session

/**
 * Persistence contract for hidden windows of real installed apps.
 * The engine does not special-case any package; every app is a task on a
 * VirtualDisplay. Tiers describe what happened to that hidden window.
 */
enum class SessionTier {
    /** Hidden task is alive on the VirtualDisplay. Viewer reattaches with zero restart. */
    LIVE,

    /**
     * GhostDisplay's process died. On next open we recreate the VirtualDisplay
     * and relaunch the same real app so it can restore via savedInstanceState.
     * Never labeled FRESH solely because GhostDisplay died.
     */
    RESURRECTED,

    /** The target app crashed or the user force-stopped it. Starts clean. */
    FRESH,
}

enum class RestoreOutcome {
    RESTORED,
    FRESH,
    UNKNOWN,
}

enum class ExistingSessionChoice {
    RESUME,
    ADDITIONAL_WINDOW,
    CANCEL,
}

object SessionPolicy {

    fun tierAfterNormalLaunch(): SessionTier = SessionTier.LIVE

    /** Process death of GhostDisplay never becomes FRESH. */
    fun tierAfterGhostProcessDeath(): SessionTier = SessionTier.RESURRECTED

    fun tierAfterTargetCrashOrForceStop(): SessionTier = SessionTier.FRESH

    fun viewerOpenOrCloseDoesNotChangeTier(current: SessionTier): SessionTier = current

    fun shouldPromptWhenActiveSessionExists(hasActiveHiddenWindow: Boolean): Boolean =
        hasActiveHiddenWindow

    fun interpretChoice(hasActive: Boolean, choice: ExistingSessionChoice?): ExistingSessionChoice {
        if (!hasActive) return ExistingSessionChoice.RESUME
        return choice ?: ExistingSessionChoice.CANCEL
    }

    fun isStopTheOnlyUserEnd(): Boolean = true

    fun classifyRestoreOutcome(
        accessibilitySawContent: Boolean?,
        looksLikeColdStart: Boolean?,
    ): RestoreOutcome = when {
        looksLikeColdStart == true -> RestoreOutcome.FRESH
        accessibilitySawContent == true -> RestoreOutcome.RESTORED
        else -> RestoreOutcome.UNKNOWN
    }
}
