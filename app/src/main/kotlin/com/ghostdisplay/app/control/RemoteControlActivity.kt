package com.ghostdisplay.app.control

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ghostdisplay.app.service.SessionRepository
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostSurface
import com.ghostdisplay.app.ui.theme.GhostTheme
import com.ghostdisplay.app.ui.theme.GhostWarn

/**
 * Blank canvas mapping user gestures to the virtual display, plus a text box
 * that injects via ACTION_SET_TEXT / clipboard paste, plus Back/Home/Recents
 * that refuse to silently hit the physical screen.
 */
class RemoteControlActivity : ComponentActivity() {

    private val controller = AccessibilityController()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val session = sessionId?.let { SessionRepository.get(it) }
        if (session == null) {
            Toast.makeText(this, "No active session to control.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            GhostTheme {
                var log by remember { mutableStateOf(session.capability.notices.joinToString("\n")) }
                var typed by remember { mutableStateOf("") }
                var goal by remember { mutableStateOf("") }
                var allowGlobal by remember { mutableStateOf(false) }
                val probe = remember {
                    controller.probe(session.displayId, session.packageName)
                }

                fun report(result: AccessibilityController.ActionResult) {
                    log = when (result) {
                        is AccessibilityController.ActionResult.Ok -> result.detail
                        is AccessibilityController.ActionResult.Failed -> result.reason
                        is AccessibilityController.ActionResult.ViewOnly -> result.reason
                    }
                    if (result is AccessibilityController.ActionResult.ViewOnly) {
                        Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Remote · ${session.appLabel}") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (probe.viewOnly || session.capability.viewOnly) {
                            Text(
                                "VIEW-ONLY on this device. Node actions and gestures cannot target the hidden app.",
                                color = GhostWarn,
                            )
                        } else if (!probe.nodeControlAvailable) {
                            Text(
                                "Node tree is empty. Gesture fallback will be tried and reported.",
                                color = GhostWarn,
                            )
                        }
                        Text(log, color = GhostMuted, style = MaterialTheme.typography.bodyMedium)

                        GesturePad(
                            displayWidth = session.config.widthPx,
                            displayHeight = session.config.heightPx,
                            enabled = !probe.viewOnly,
                            onTap = { x, y -> report(controller.click(session.displayId, x, y)) },
                            onLong = { x, y -> report(controller.longPress(session.displayId, x, y)) },
                            onSwipe = { x1, y1, x2, y2 ->
                                report(controller.swipe(session.displayId, x1, y1, x2, y2))
                            },
                        )

                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Inject text") },
                            placeholder = { Text("Focus a field in the hidden app, then inject") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                report(controller.setText(session.displayId, typed, session.packageName))
                            }) { Text("Set text") }
                            OutlinedButton(onClick = {
                                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("ghost", typed))
                                report(controller.pasteClipboard(session.displayId, session.packageName))
                            }) { Text("Paste") }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                report(controller.back(session.displayId, session.packageName, allowGlobal))
                            }) { Text("Back") }
                            OutlinedButton(onClick = { report(controller.home(allowGlobal)) }) { Text("Home") }
                            OutlinedButton(onClick = { report(controller.recents(allowGlobal)) }) { Text("Recents") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = allowGlobal, onCheckedChange = { allowGlobal = it })
                            Text(
                                "Allow system Back/Home/Recents (NOT scoped to the virtual display — may affect the app on this screen)",
                                color = GhostWarn,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }

                        Text("Task mode (best-effort)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Optional. Example: “open site example.com and click play”. Uses node actions only; not an agent.",
                            color = GhostMuted,
                        )
                        OutlinedTextField(
                            value = goal,
                            onValueChange = { goal = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Goal") },
                        )
                        Button(onClick = {
                            val results = controller.runTask(session.displayId, session.packageName, goal)
                            log = results.joinToString("\n") {
                                when (it) {
                                    is AccessibilityController.ActionResult.Ok -> "ok: ${it.detail}"
                                    is AccessibilityController.ActionResult.Failed -> "fail: ${it.reason}"
                                    is AccessibilityController.ActionResult.ViewOnly -> "view-only: ${it.reason}"
                                }
                            }
                        }) { Text("Run task") }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}

@Composable
private fun GesturePad(
    displayWidth: Int,
    displayHeight: Int,
    enabled: Boolean,
    onTap: (Float, Float) -> Unit,
    onLong: (Float, Float) -> Unit,
    onSwipe: (Float, Float, Float, Float) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var last by remember { mutableStateOf<Offset?>(null) }
    var start by remember { mutableStateOf<Offset?>(null) }

    fun map(p: Offset): Pair<Float, Float> {
        val mapped = CoordinateMapper.canvasToDisplay(
            p.x, p.y,
            size.width.toFloat(), size.height.toFloat(),
            displayWidth, displayHeight,
        )
        return mapped.x to mapped.y
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(GhostSurface)
            .onSizeChanged { size = it }
            .pointerInput(enabled, displayWidth, displayHeight, size) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onLongPress = { off ->
                        val (x, y) = map(off)
                        onLong(x, y)
                    },
                    onTap = { off ->
                        val (x, y) = map(off)
                        onTap(x, y)
                    },
                )
            }
            .pointerInput(enabled, displayWidth, displayHeight, size) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        start = it
                        last = it
                    },
                    onDragEnd = {
                        val s = start
                        val e = last
                        if (s != null && e != null) {
                            val a = map(s)
                            val b = map(e)
                            onSwipe(a.first, a.second, b.first, b.second)
                        }
                    },
                    onDrag = { change, _ ->
                        last = change.position
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val aspect = displayWidth.toFloat() / displayHeight.toFloat().coerceAtLeast(1f)
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val (cw, ch) = if (w / h > aspect) (h * aspect) to h else w to (w / aspect)
            val l = (w - cw) / 2f
            val t = (h - ch) / 2f
            drawRect(
                color = Color(0x227AE7C7),
                topLeft = Offset(l, t),
                size = androidx.compose.ui.geometry.Size(cw, ch),
            )
        }
        Text(
            if (enabled) "Hidden display · $displayWidth×$displayHeight\nTap / drag to control"
            else "View-only — gestures disabled",
            color = GhostMint,
        )
    }
}
