package com.ghostdisplay.app.session

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ghostdisplay.app.control.RemoteControlActivity
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.service.SessionRepository
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostTheme
import android.content.Intent

/**
 * Window onto an existing hidden session. Opening or closing this activity
 * must not pause, background, or recreate the target app.
 */
class AppViewerActivity : ComponentActivity() {

    private var sessionId: String? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOpen = true
        val id = intent.getStringExtra(EXTRA_SESSION_ID)
        sessionId = id
        val session = id?.let { SessionRepository.get(it) }
        val engine = GhostService.instance?.engine()
        if (id == null || session == null || !ViewerReattach.canReattach(engine, id)) {
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
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Hidden window · ${session.appLabel}") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    startActivity(
                                        Intent(this@AppViewerActivity, RemoteControlActivity::class.java)
                                            .putExtra(RemoteControlActivity.EXTRA_SESSION_ID, id),
                                    )
                                }) { Text("Ctrl") }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                        Text(
                            "This is your real ${session.appLabel}, same account, running on a hidden display. " +
                                "Reopen it here to pick up exactly where you left off. Closing this viewer does not stop the hidden window.",
                            color = GhostMint,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            "${session.tier}" +
                                (session.restoreOutcome?.let { " · last relaunch: $it" } ?: "") +
                                (session.categoryCaveat?.let { "\n$it" } ?: ""),
                            color = GhostMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    holder.addCallback(object : SurfaceHolder.Callback {
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
                                }
                            },
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
