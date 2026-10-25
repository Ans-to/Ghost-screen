package com.ghostdisplay.app.display

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.service.SessionRepository
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostTheme
import com.ghostdisplay.app.ui.theme.GhostWarn
import java.nio.ByteBuffer

/**
 * Explicit, opt-in look at the virtual display. Not mirroring to display 0
 * automatically, not recording, not exfiltrating. Closed → surface restored
 * to the engine ImageReader so the hidden app keeps running.
 */
class DebugPreviewActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val session = sessionId?.let { SessionRepository.get(it) }
        val engine = GhostService.instance?.engine()
        val handle = sessionId?.let { engine?.get(it) }
        if (session == null || handle == null) {
            Toast.makeText(this, "No hidden session to preview.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            GhostTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Debug preview · ${session.appLabel}") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                        Text(
                            "This is the only time the hidden app is shown on this screen. Close this activity to keep it hidden. Frames are not saved.",
                            color = GhostWarn,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            "Display ${session.displayId} · ${session.config.widthPx}×${session.config.heightPx}",
                            color = GhostMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        PreviewSurface(
                            sessionId = handle.sessionId,
                            width = handle.config.widthPx,
                            height = handle.config.heightPx,
                            imageReader = handle.surface.imageReader,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId != null) {
            GhostService.instance?.engine()?.restoreEngineSurface(sessionId)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}

@Composable
private fun PreviewSurface(
    sessionId: String,
    width: Int,
    height: Int,
    imageReader: android.media.ImageReader?,
) {
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(sessionId) {
        val handler = Handler(Looper.getMainLooper())
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (_: Throwable) {
                null
            } ?: return@setOnImageAvailableListener
            try {
                frame = image.toBitmap()
            } finally {
                image.close()
            }
        }, handler)
        onDispose {
            imageReader?.setOnImageAvailableListener(null, null)
        }
    }

    val bmp = frame
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Hidden display preview",
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            GhostService.instance?.engine()?.setSurface(sessionId, holder.surface)
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            GhostService.instance?.engine()?.restoreEngineSurface(sessionId)
                        }
                    })
                }
            },
        )
        Text("Waiting for a frame, or showing a live SurfaceView swap.", color = GhostMuted)
    }
}

private fun Image.toBitmap(): Bitmap? {
    if (format != PixelFormat.RGBA_8888 && format != 1 /* RGBA_8888 some devices */) {
        // ImageReader was created as RGBA_8888; if the plane layout is unexpected, skip.
    }
    val plane = planes.firstOrNull() ?: return null
    val buffer: ByteBuffer = plane.buffer
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(buffer)
    return if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height)
}
