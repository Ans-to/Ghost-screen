package com.ghostdisplay.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ghostdisplay.app.browser.BrowserRuntime
import com.ghostdisplay.app.browser.TabLiveStatus
import com.ghostdisplay.app.browser.TabPolicy
import com.ghostdisplay.app.browser.UrlNormalizer
import com.ghostdisplay.app.browser.ViewerActivity
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostWarn
import kotlinx.coroutines.launch

@Composable
fun BrowserHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick by BrowserRuntime.tick.collectAsState()
    val tm = BrowserRuntime.tabManager
    val tabs = tm?.tabs?.collectAsState()?.value ?: emptyList()
    val session = tm?.session?.collectAsState()?.value
    val restore by BrowserRuntime.restore.collectAsState()
    val mem = remember(tick, tabs) { BrowserRuntime.memory?.read(tabs) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Persistent Browser", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This is GhostDisplay’s primary mode: a WebView tab engine on a hidden virtual display. " +
                "Tabs are created once and never destroyed until you close them here. " +
                "Chrome/Firefox are a separate External Browser mode and may discard their own tabs — we cannot stop that.",
            color = GhostMuted,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (BrowserRuntime.running) "Engine running hidden" else "Engine stopped",
                    color = if (BrowserRuntime.running) GhostMint else GhostWarn,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("${tabs.size} tabs · ${tabs.count { it.liveStatus == TabLiveStatus.LIVE }} live · ${tabs.count { it.audioPlaying }} playing audio")
                mem?.let {
                    LinearProgressIndicator(progress = it.fraction, modifier = Modifier.fillMaxWidth())
                    Text(it.label, color = GhostMuted)
                }
                restore?.let {
                    Text("Last restore: ${it.live.size} live, ${it.pausedBySystem.size} paused by system. Records dropped: 0.", color = GhostMint)
                }
                BrowserRuntime.presentationNotice?.let { Text(it, color = GhostWarn) }
                Button(
                    onClick = { GhostService.startBrowser(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (BrowserRuntime.running) "Ensure engine is running" else "Start persistent browser") }
                Button(
                    onClick = {
                        context.startActivity(Intent(context, ViewerActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = BrowserRuntime.running || tabs.isNotEmpty(),
                ) { Text("Open in Viewer") }
                OutlinedButton(
                    onClick = { GhostService.stopBrowser(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = BrowserRuntime.running,
                ) { Text("Stop engine (tabs are kept and restored next start)") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Max tabs: ${session?.maxTabs ?: TabPolicy.DEFAULT_MAX_TABS} (hard cap ${TabPolicy.HARD_MAX_TABS})", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = (session?.maxTabs ?: TabPolicy.DEFAULT_MAX_TABS).toFloat(),
                    onValueChange = { v ->
                        scope.launch { tm?.setMaxTabs(v.toInt()) }
                    },
                    valueRange = 1f..TabPolicy.HARD_MAX_TABS.toFloat(),
                    steps = TabPolicy.HARD_MAX_TABS - 2,
                )
                Text("Default pool is 10. Raising past 8 warns; past 10 warns again. GhostDisplay will not auto-close tabs to free RAM.", color = GhostMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Master mute", modifier = Modifier.weight(1f))
                    Switch(
                        checked = session?.masterMuted == true,
                        onCheckedChange = { v -> scope.launch { tm?.setMasterMuted(v) } },
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Known limits", style = MaterialTheme.typography.titleMedium)
                Text("• DRM (Netflix and similar) may refuse a virtual display. Per-tab warning + keep-visible shim, which is best-effort.", color = GhostMuted)
                Text("• Some players check document.visibilityState. The shim overrides it; it cannot defeat DRM.", color = GhostMuted)
                Text("• 10+ video-heavy tabs on <4 GB RAM may be killed by Android. Tabs are restored; playback position may reset.", color = GhostMuted)
                Text("• Closing the Viewer does not pause anything. Locking the phone does not pause anything.", color = GhostMuted)
            }
        }

        Text("Sessions — running tabs", style = MaterialTheme.typography.titleLarge)
        if (tabs.isEmpty()) {
            Text("No tabs yet. Open the Viewer and tap +.", color = GhostMuted)
        } else {
            tabs.forEach { tab ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(tab.displayTitle, style = MaterialTheme.typography.titleMedium)
                        Text(UrlNormalizer.hostOf(tab.url), color = GhostMuted)
                        val status = buildString {
                            append(if (tab.liveStatus == TabLiveStatus.LIVE) "live" else "paused by system, tap to resume")
                            if (tab.audioPlaying) append(" · playing audio")
                            if (tab.pinned) append(" · pinned")
                            if (tab.muted) append(" · muted")
                            if (tab.liteMode) append(" · lite")
                        }
                        Text(status, color = if (tab.liveStatus == TabLiveStatus.LIVE) GhostMint else GhostWarn)
                        Button(onClick = {
                            context.startActivity(
                                Intent(context, ViewerActivity::class.java)
                                    .putExtra(ViewerActivity.EXTRA_TAB_ID, tab.id),
                            )
                        }) { Text("Open in Viewer") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ExternalBrowserBanner() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("External Browser mode", style = MaterialTheme.typography.titleMedium, color = GhostWarn)
            Text(
                "Launching Chrome, Firefox, or another installed browser on a virtual display cannot keep 10 tabs alive. That browser owns its tab lifecycle and may discard, freeze, or reload background tabs. Use Persistent Browser for guaranteed tabs.",
                color = GhostMuted,
            )
        }
    }
}
