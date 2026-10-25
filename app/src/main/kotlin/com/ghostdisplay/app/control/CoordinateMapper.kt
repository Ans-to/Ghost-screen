package com.ghostdisplay.app.control

/**
 * Maps a point on the Remote Control canvas (in canvas pixels) to the
 * virtual display's pixel space. Pure math — unit tested.
 */
object CoordinateMapper {

    data class Point(val x: Float, val y: Float)

    fun canvasToDisplay(
        canvasX: Float,
        canvasY: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        displayWidth: Int,
        displayHeight: Int,
    ): Point {
        if (canvasWidth <= 0f || canvasHeight <= 0f || displayWidth <= 0 || displayHeight <= 0) {
            return Point(0f, 0f)
        }
        val scale = minOf(
            canvasWidth / displayWidth.toFloat(),
            canvasHeight / displayHeight.toFloat(),
        )
        val contentW = displayWidth * scale
        val contentH = displayHeight * scale
        val offsetX = (canvasWidth - contentW) / 2f
        val offsetY = (canvasHeight - contentH) / 2f
        val x = ((canvasX - offsetX) / scale).coerceIn(0f, displayWidth.toFloat() - 1f)
        val y = ((canvasY - offsetY) / scale).coerceIn(0f, displayHeight.toFloat() - 1f)
        return Point(x, y)
    }

    fun normalize(x: Float, y: Float, displayWidth: Int, displayHeight: Int): Point {
        val w = displayWidth.coerceAtLeast(1)
        val h = displayHeight.coerceAtLeast(1)
        return Point(
            (x / w).coerceIn(0f, 1f),
            (y / h).coerceIn(0f, 1f),
        )
    }
}
