package com.ghostdisplay.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioBuffersTest {
    @Test
    fun neverUsesUnalignedSampleRateHalf() {
        // 44100/2 = 22050, which is not a multiple of 4 (stereo 16-bit).
        val bytes = alignedAudioBufferBytes(minIn = 8192, minOut = 8192, bytesPerFrame = 4)
        assertThat(bytes % 4).isEqualTo(0)
        assertThat(bytes).isAtLeast(8192)
        assertThat(bytes).isNotEqualTo(22050)
    }
}
