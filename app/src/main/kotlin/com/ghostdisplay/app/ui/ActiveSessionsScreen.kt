package com.ghostdisplay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.ghostdisplay.app.browser.BrowserRuntime
import com.ghostdisplay.app.browser.TabLiveStatus
import com.ghostdisplay.app.browser.ViewerActivity
import com.ghostdisplay.app.GhostApp
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.SessionStatus

import com.ghostdisplay.app.ui.theme.GhostDanger
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostWarn

@Composable
fun ActiveSessionsScreen(
    sessions: List<GhostSession>,
    onRemote: (GhostSession) -> Unit,
    onPreview: (GhostSession) -> Unit,
    onStop: (GhostSession) -> Unit,
    onStopAll: () -> Unit,
    onMute: (GhostSession, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val browserTabs = BrowserRuntime.tabManager?.tabs?.collectAsState()?.value ?: emptyList()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active sessions", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (sessions.any { it.status == SessionStatus.RUNNING } || BrowserRuntime.running) {
                OutlinedButton(onClick = onStopAll) { Text("Kill switch") }
            }
        }
        Text(
            "A hidden window is your real app on a virtual display. Closing the Viewer does not stop it. Stop is the only user end — it fully releases the display, wake lock, MediaProjection and foreground service.",
            color = GhostMuted,
        )
        Spacer(Modifier.height(12.dp))
        if (browserTabs.isNotEmpty() || BrowserRuntime.running) {
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Persistent Browser", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${browserTabs.size} tabs · ${browserTabs.count { it.liveStatus == TabLiveStatus.LIVE }} live · ${browserTabs.count { it.audioPlaying }} playing audio",
                        color = GhostMint,
                    )
                    Button(onClick = {
                        context.startActivity(Intent(context, ViewerActivity::class.java))
                    }) { Text("Open in Viewer") }
                }
            }
        }
        if (sessions.isEmpty() && browserTabs.isEmpty()) {
            Text("No sessions. Hold an app on the home screen or in the app list, then choose Run hidden.", color = GhostMuted)
        } else if (sessions.isNotEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(session, onRemote, onPreview, onStop, onMute)
                }
                item { SessionLogCard() }
            }
        }
    }
}

@Composable
private fun SessionLogCard() {
    val app = LocalContext.current.applicationContext as GhostApp
    val logs by app.container.db.sessionLog().observeLatest(20).collectAsState(initial = emptyList())
    if (logs.isEmpty()) return
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Session log", style = MaterialTheme.typography.titleMedium)
            logs.forEach { entry ->
                Text(
                    "${entry.event} · ${entry.packageName}" + (entry.detail?.let { " · $it" } ?: ""),
                    color = GhostMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: GhostSession,
    onRemote: (GhostSession) -> Unit,
    onPreview: (GhostSession) -> Unit,
    onStop: (GhostSession) -> Unit,
    onMute: (GhostSession, Boolean) -> Unit,
) {
    val cap = session.capability
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session.appLabel, style = MaterialTheme.typography.titleLarge)
                    Text(session.packageName, color = GhostMuted)
                }
                CompatChip(cap.compatibility)
            }
            StatusLine(session)
            Text(
                "Tier ${session.tier}" +
                    (session.restoreOutcome?.let { " · last relaunch: $it" } ?: "") +
                    (session.lastViewedAt?.let { " · viewed" } ?: ""),
                color = GhostMint,
            )
            session.categoryCaveat?.let { Text(it, color = GhostMuted) }
            Text(
                "Display ${session.displayId} · ${session.config.widthPx}×${session.config.heightPx} @${session.config.densityDpi}dpi · ${session.config.orientation} · ${session.config.surfaceMode}",
                color = GhostMuted,
            )
            if (cap.viewOnly) {
                Text("VIEW-ONLY on this device. Remote Control cannot click or type.", color = GhostWarn)
            }
            cap.notices.forEach { notice ->
                Text("• $notice", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mute audio", modifier = Modifier.weight(1f))
                Switch(checked = session.muted, onCheckedChange = { onMute(session, it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPreview(session) }, enabled = session.status == SessionStatus.RUNNING) {
                    Text("Open Viewer")
                }
                OutlinedButton(onClick = { onRemote(session) }, enabled = session.status == SessionStatus.RUNNING) {
                    Text("Remote Control")
                }
            }
            Button(onClick = { onStop(session) }) { Text("Stop") }
        }
    }
}

@Composable
private fun StatusLine(session: GhostSession) {
    val (label, color) = when (session.status) {
        SessionStatus.RUNNING -> "Running hidden · ${session.tier}" to GhostMint
        SessionStatus.STARTING -> "Starting" to GhostWarn
        SessionStatus.PAUSED -> "Paused" to GhostWarn
        SessionStatus.UNSUPPORTED -> "Unsupported on this device" to GhostDanger
        SessionStatus.ERROR -> "Error" to GhostDanger
        SessionStatus.STOPPED -> "Stopped" to GhostMuted
    }
    Text(label, color = color, style = MaterialTheme.typography.titleMedium)
}
