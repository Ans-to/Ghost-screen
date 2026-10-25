package com.ghostdisplay.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** OEM adaptive icons sometimes throw from toBitmap; never crash the hold sheet. */
fun Drawable.safeImageBitmap(size: Int): ImageBitmap? {
    return try {
        mutate().toBitmap(size, size).asImageBitmap()
    } catch (_: Throwable) {
        try {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            setBounds(0, 0, size, size)
            draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
}
