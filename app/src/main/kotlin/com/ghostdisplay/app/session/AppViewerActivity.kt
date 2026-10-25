package com.ghostdisplay.app.session

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ghostdisplay.app.control.AccessibilityController
import com.ghostdisplay.app.control.CoordinateMapper
import com.ghostdisplay.app.control.RemoteControlActivity
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.service.SessionRepository
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostTheme
import com.ghostdisplay.app.ui.theme.GhostWarn

/**
 * Window onto an existing hidden session. Opening or closing this activity
 * must not pause, background, or recreate the target app.
 *
 * Taps, holds, and swipes go to the hidden window so Chrome’s own tab
 * switcher (hold a tab to select it) works here.
 */
class AppViewerActivity : ComponentActivity() {

    private var sessionId: String? = null
    private val controller = AccessibilityController()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOpen = true
        val id = intent.getStringExtra(EXTRA_SESSION_ID)
        sessionId = id
        val initial = id?.let { SessionRepository.get(it) }
        val engine = GhostService.instance?.engine()
        if (id == null || initial == null || !ViewerReattach.canReattach(engine, id)) {
            Toast.makeText(
                this,
                "That hidden window is not live. If GhostDisplay was killed, reopen the session — it will be labeled RESURRECTED.",
                Toast.LENGTH_LONG,
            ).show()
            finish()
            return
        }
        setContent {
            GhostTheme {
                val sessions by SessionRepository.sessions.collectAsState()
                val session = sessions.firstOrNull { it.id == id } ?: initial
                val dims = remember {
                    intArrayOf(session.config.widthPx, session.config.heightPx, session.displayId)
                }
                dims[0] = session.config.widthPx
                dims[1] = session.config.heightPx
                dims[2] = session.displayId

                fun report(result: AccessibilityController.ActionResult) {
                    if (result is AccessibilityController.ActionResult.ViewOnly) {
                        Toast.makeText(this@AppViewerActivity, result.reason, Toast.LENGTH_LONG).show()
                    }
                }

                fun openTabSwitcher() {
                    val labels = listOf(
                        "Switch or close tabs",
                        "Tabs",
                        "Open tabs",
                        "Tab switcher",
                    )
                    var ok = false
                    for (label in labels) {
                        when (val r = controller.clickByText(session.displayId, label, session.packageName)) {
                            is AccessibilityController.ActionResult.Ok -> {
                                ok = true
                                break
                            }
                            is AccessibilityController.ActionResult.ViewOnly -> {
                                report(r)
                                return
                            }
                            else -> Unit
                        }
                    }
                    if (!ok) {
                        Toast.makeText(
                            this@AppViewerActivity,
                            "Could not open the tab switcher from here. Tap the tab button on the hidden window, then hold a tab to select it.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(session.appLabel) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { openTabSwitcher() }) {
                                    Icon(Icons.Outlined.GridView, contentDescription = "Tabs")
                                }
                                IconButton(onClick = {
                                    startActivity(
                                        Intent(this@AppViewerActivity, RemoteControlActivity::class.java)
                                            .putExtra(RemoteControlActivity.EXTRA_SESSION_ID, id),
                                    )
                                }) {
                                    Icon(Icons.Outlined.Keyboard, contentDescription = "Type")
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Text(
                            "Hold a tab to select it. Tap to open. Closing this viewer does not stop the hidden window.",
                            color = GhostMint,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        if (session.capability.viewOnly) {
                            Text(
                                "VIEW-ONLY on this device. Holds and taps cannot reach the hidden window.",
                                color = GhostWarn,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            factory = { ctx ->
                                val frame = FrameLayout(ctx)
                                val surface = SurfaceView(ctx)
                                val overlay = View(ctx)
                                frame.addView(
                                    surface,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                                frame.addView(
                                    overlay,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                                surface.holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        ViewerReattach.attach(GhostService.instance?.engine(), id, holder.surface)
                                    }
                                    override fun surfaceChanged(
                                        holder: SurfaceHolder,
                                        format: Int,
                                        width: Int,
                                        height: Int,
                                    ) = Unit
                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        ViewerReattach.detach(GhostService.instance?.engine(), id)
                                    }
                                })
                                fun map(x: Float, y: Float, vw: Int, vh: Int): Pair<Float, Float> {
                                    val mapped = CoordinateMapper.canvasToDisplay(
                                        x, y, vw.toFloat(), vh.toFloat(), dims[0], dims[1],
                                    )
                                    return mapped.x to mapped.y
                                }
                                val detector = GestureDetector(
                                    ctx,
                                    object : GestureDetector.SimpleOnGestureListener() {
                                        override fun onDown(e: MotionEvent): Boolean = true
                                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                                            val (x, y) = map(e.x, e.y, overlay.width, overlay.height)
                                            report(controller.click(dims[2], x, y))
                                            return true
                                        }
                                        override fun onLongPress(e: MotionEvent) {
                                            overlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            val (x, y) = map(e.x, e.y, overlay.width, overlay.height)
                                            report(controller.longPress(dims[2], x, y))
                                        }
                                        override fun onFling(
                                            e1: MotionEvent?,
                                            e2: MotionEvent,
                                            velocityX: Float,
                                            velocityY: Float,
                                        ): Boolean {
                                            if (e1 == null) return false
                                            val a = map(e1.x, e1.y, overlay.width, overlay.height)
                                            val b = map(e2.x, e2.y, overlay.width, overlay.height)
                                            report(controller.swipe(dims[2], a.first, a.second, b.first, b.second))
                                            return true
                                        }
                                    },
                                )
                                overlay.setOnTouchListener { _, ev ->
                                    detector.onTouchEvent(ev)
                                    true
                                }
                                frame
                            },
                        )
                        Text(
                            session.tier + (session.restoreOutcome?.let { " · last relaunch: $it" } ?: ""),
                            color = GhostMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isOpen = true
        sessionId?.let { GhostService.instance?.onViewerOpened(it) }
    }

    override fun onPause() {
        isOpen = false
        super.onPause()
    }

    override fun onDestroy() {
        isOpen = false
        sessionId?.let { ViewerReattach.detach(GhostService.instance?.engine(), it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"

        @Volatile
        var isOpen: Boolean = false
    }
}
