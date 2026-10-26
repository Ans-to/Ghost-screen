package com.ghostdisplay.app.launch

import android.app.Activity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Android 10+ refuses [Activity.startActivity] from a Service.
 * HoldActionActivity / MainActivity register here.
 *
 * The screen-capture consent activity stops our UI. 1.3.9 cleared the host
 * in onStop, then GhostService tried to launch while nothing was resumed —
 * that is the “GhostDisplay was not on screen” lastError. A pin survives
 * onStop for the activity that started the consent flow; launch waits for
 * that activity to exist (and prefers it once it is started again).
 */
object ForegroundLaunchHost {

    private val current = AtomicReference<Activity?>(null)
    private val pinned = AtomicReference<Activity?>(null)
    private val waiter = AtomicReference<CompletableDeferred<Activity>?>(null)

    fun onResume(activity: Activity) {
        current.set(activity)
        waiter.get()?.complete(activity)
    }

    fun onPause(activity: Activity) {
        if (pinned.get() === activity) return
        current.compareAndSet(activity, null)
    }

    fun pin(activity: Activity) {
        pinned.set(activity)
        if (!activity.isFinishing && !activity.isDestroyed) {
            current.set(activity)
        }
    }

    fun unpin() {
        pinned.set(null)
    }

    fun live(): Activity? = usable(current.get()) ?: usable(pinned.get())

    suspend fun requireResumed(timeoutMs: Long = 3_000L): Activity {
        live()?.let { return it }
        val deferred = CompletableDeferred<Activity>()
        waiter.set(deferred)
        live()?.let { return it }
        val got = withTimeoutOrNull(timeoutMs) { deferred.await() }
        waiter.compareAndSet(deferred, null)
        val activity = usable(got) ?: live()
        if (activity == null) {
            throw IllegalStateException(
                "Android blocked starting another app because GhostDisplay was not on screen. " +
                    "A background service is not allowed to open another app onto the hidden display. " +
                    "Keep GhostDisplay open until the hidden window starts.",
            )
        }
        return activity
    }

    private fun usable(activity: Activity?): Activity? {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return null
        return activity
    }
}
