package com.ghostdisplay.app.control

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoordinateMapperTest {

    @Test
    fun mapsCenterOfLetterboxedCanvas() {
        // Canvas 200x100, display 100x100 → content is 100x100 centered, offsetX=50
        val p = CoordinateMapper.canvasToDisplay(
            canvasX = 100f,
            canvasY = 50f,
            canvasWidth = 200f,
            canvasHeight = 100f,
            displayWidth = 100,
            displayHeight = 100,
        )
        assertThat(p.x).isWithin(0.01f).of(50f)
        assertThat(p.y).isWithin(0.01f).of(50f)
    }

    @Test
    fun clampsOutsideContent() {
        val p = CoordinateMapper.canvasToDisplay(
            canvasX = 0f,
            canvasY = 0f,
            canvasWidth = 200f,
            canvasHeight = 100f,
            displayWidth = 100,
            displayHeight = 100,
        )
        assertThat(p.x).isEqualTo(0f)
        assertThat(p.y).isEqualTo(0f)
    }

    @Test
    fun normalize() {
        val n = CoordinateMapper.normalize(540f, 960f, 1080, 1920)
        assertThat(n.x).isWithin(0.001f).of(0.5f)
        assertThat(n.y).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun zeroCanvasDoesNotCrash() {
        val p = CoordinateMapper.canvasToDisplay(1f, 1f, 0f, 0f, 100, 100)
        assertThat(p.x).isEqualTo(0f)
    }
}
