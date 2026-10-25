package com.ghostdisplay.app.display

import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.Surface

/**
 * Owns the Surface that backs a VirtualDisplay.
 *
 * Two modes:
 *  - IMAGE_READER: an ImageReader. The latest frame can be shown in the
 *    explicit debug preview. Frames are never written to disk.
 *  - MEDIA_PROJECTION: still an ImageReader (so we can swap / preview) but the
 *    VirtualDisplay itself is created through MediaProjection so the system
 *    treats it as a capture-capable, optionally-secure display.
 *
 * [swap] replaces the surface the VirtualDisplay is looking at without tearing
 * down the display — the target app should keep running.
 */
class GhostSurface(
    val width: Int,
    val height: Int,
    val mode: DisplayConfig.SurfaceMode,
) : AutoCloseable {

    private var reader: ImageReader? = ImageReader.newInstance(
        width,
        height,
        PixelFormat.RGBA_8888,
        /* maxImages */ 3,
    )

    val surface: Surface
        get() = reader?.surface ?: error("Surface already released")

    val imageReader: ImageReader?
        get() = reader

    fun enablePreviewListener(handler: Handler = Handler(Looper.getMainLooper()), onFrame: () -> Unit) {
        reader?.setOnImageAvailableListener(
            { onFrame() },
            handler,
        )
    }

    fun clearPreviewListener() {
        reader?.setOnImageAvailableListener(null, null)
    }

    override fun close() {
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
    }
}
