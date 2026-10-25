package com.ghostdisplay.app.audio

fun alignedAudioBufferBytes(minIn: Int, minOut: Int, bytesPerFrame: Int): Int {
    val frame = bytesPerFrame.coerceAtLeast(1)
    val raw = maxOf(minIn, minOut, frame) * 2
    return ((raw + frame - 1) / frame) * frame
}
