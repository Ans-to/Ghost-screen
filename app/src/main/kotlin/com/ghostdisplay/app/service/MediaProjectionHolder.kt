package com.ghostdisplay.app.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * MediaProjection consent can only be obtained from an Activity, but the
 * VirtualDisplay lives in [GhostService]. This holder is the hand-off.
 *
 * The token is single-use on modern Android — after process death the user
 * must grant the dialog again. We never pretend otherwise.
 */
object MediaProjectionHolder {
    @Volatile
    var resultCode: Int = Activity.RESULT_CANCELED
        private set

    @Volatile
    var resultData: Intent? = null
        private set

    @Volatile
    private var projection: MediaProjection? = null

    fun store(resultCode: Int, data: Intent?) {
        this.resultCode = resultCode
        this.resultData = data
        projection = null
    }

    fun hasConsent(): Boolean = resultCode == Activity.RESULT_OK && resultData != null

    fun get(context: Context): MediaProjection? {
        projection?.let { return it }
        val data = resultData ?: return null
        if (resultCode != Activity.RESULT_OK) return null
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try {
            mgr.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            null
        }
        if (mp != null && Build.VERSION.SDK_INT >= 34) {
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    clear()
                }
            }, Handler(Looper.getMainLooper()))
        }
        projection = mp
        return mp
    }

    fun clear() {
        runCatching { projection?.stop() }
        projection = null
        resultCode = Activity.RESULT_CANCELED
        resultData = null
    }
}
