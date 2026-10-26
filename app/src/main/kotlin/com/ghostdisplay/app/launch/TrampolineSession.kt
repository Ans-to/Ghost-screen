package com.ghostdisplay.app.launch

import android.view.Display
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handshake between [AppLauncher] and [DisplayTrampolineActivity].
 *
 * startActivity returning without an exception does **not** mean the starter
 * is on the virtual display. HyperOS can ignore launchDisplayId and still
 * place our activity on the phone. The trampoline reports the display it
 * actually landed on; Chrome is started only after that display is the
 * hidden one.
 */
class TrampolineSession(
    val packageName: String,
    val activityName: String,
    val expectedDisplayId: Int,
) {
    @Volatile var taskId: Int? = null
        private set

    @Volatile var lastDisplayId: Int = Display.DEFAULT_DISPLAY
        private set

    private val finished = CompletableDeferred<Outcome>()

    fun noteLanded(displayId: Int, taskId: Int) {
        lastDisplayId = displayId
        this.taskId = taskId
    }

    fun succeed(displayId: Int, taskId: Int) {
        finished.complete(Outcome.Ok(displayId, taskId))
    }

    fun fail(message: String) {
        finished.complete(Outcome.Err(message, lastDisplayId))
    }

    suspend fun awaitFinished(timeoutMs: Long): Outcome? =
        withTimeoutOrNull(timeoutMs) { finished.await() }

    suspend fun awaitTaskId(timeoutMs: Long): Int? {
        val step = 40L
        var waited = 0L
        while (waited < timeoutMs) {
            taskId?.let { return it }
            delay(step)
            waited += step
        }
        return taskId
    }

    fun alreadyFinished(): Boolean = finished.isCompleted

    sealed class Outcome {
        data class Ok(val displayId: Int, val taskId: Int) : Outcome()
        data class Err(val message: String, val displayId: Int) : Outcome()
    }

    companion object {
        @Volatile
        var current: TrampolineSession? = null
    }
}
