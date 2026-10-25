package com.ghostdisplay.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class OcrHit(
    val text: String,
    val centerX: Float,
    val centerY: Float,
)

/**
 * Optional OCR fallback for apps that expose no accessibility tree.
 * Frames are processed in memory and never written to disk.
 */
interface OcrFallback {
    suspend fun findText(bitmap: Bitmap, query: String): OcrHit?
}

class MlKitOcrFallback : OcrFallback {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun findText(bitmap: Bitmap, query: String): OcrHit? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null
        val text = suspendCancellableCoroutine<Text?> { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result -> cont.resume(result) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return null
        val block = text.textBlocks.firstOrNull { it.text.lowercase().contains(needle) }
            ?: return null
        val box = block.boundingBox ?: return null
        return OcrHit(
            text = block.text,
            centerX = box.exactCenterX(),
            centerY = box.exactCenterY(),
        )
    }
}
