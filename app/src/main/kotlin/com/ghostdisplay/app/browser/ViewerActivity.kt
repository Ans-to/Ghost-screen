package com.ghostdisplay.app.browser

import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ghostdisplay.app.control.CoordinateMapper
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.ui.theme.GhostDanger
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostSurface
import com.ghostdisplay.app.ui.theme.GhostTheme
import com.ghostdisplay.app.ui.theme.GhostWarn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The only place the persistent browser is shown on the physical screen.
 * Closing this activity does not pause, hide-GONE, or destroy any tab.
 */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BrowserRuntime.tabManager == null) {
            GhostService.startBrowser(this)
        }
        setContent {
            GhostTheme {
                ViewerScreen(onFinish = { finish() })
            }
        }
    }

    override fun onDestroy() {
        val id = BrowserRuntime.displaySessionId
        if (id != null) {
            GhostService.instance?.engine()?.restoreEngineSurface(id)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TAB_ID = "tab_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ViewerScreen(onFinish: () -> Unit) {
    val tick by BrowserRuntime.tick.collectAsState()
    val tm = BrowserRuntime.tabManager
    val host = BrowserRuntime.host
    val tabs = tm?.tabs?.collectAsState()?.value ?: emptyList()
    val activeId = tm?.activeId?.collectAsState()?.value
    val session = tm?.session?.collectAsState()?.value
    val notices = tm?.notices?.collectAsState()?.value ?: emptyList()
    val restore = BrowserRuntime.restore.collectAsState().value
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var inject by remember { mutableStateOf("") }
    var showSwitcher by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf<TabRecord?>(null) }
    var pendingNewWarnings by remember { mutableStateOf<List<String>?>(null) }
    var longPressTab by remember { mutableStateOf<TabRecord?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val active = tabs.firstOrNull { it.id == activeId }

    LaunchedEffect(active?.url) {
        if (active != null) address = active.url
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            BrowserRuntime.pulse()
            tm?.snapshotAll()
        }
    }

    val mem = remember(tick, tabs) {
        BrowserRuntime.memory?.read(tabs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Persistent Browser") },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close viewer")
                    }
                },
                actions = {
                    TextButton(onClick = { showHistory = true }) { Text("Log") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (BrowserRuntime.presentationNotice != null) {
                Text(BrowserRuntime.presentationNotice!!, color = GhostWarn, modifier = Modifier.padding(8.dp))
            }
            restore?.let { r ->
                Text(
                    "Restored ${r.live.size} live, ${r.pausedBySystem.size} paused by system. No tab records dropped.",
                    color = GhostMint,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            MemoryBar(mem)
            TabStrip(
                tabs = tabs,
                activeId = activeId,
                onSelect = { scope.launch { tm?.select(it) } },
                onCloseRequest = { pendingClose = it },
                onLongPress = { longPressTab = it },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { activeId?.let { tm?.goBack(it) } }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { activeId?.let { tm?.goForward(it) } }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { activeId?.let { tm?.reload(it) } }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Address") },
                )
                TextButton(onClick = {
                    val id = activeId
                    if (id != null) scope.launch { tm?.loadUrl(id, address) }
                }) { Text("Go") }
            }
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = {
                    scope.launch {
                        when (val r = tm?.newTab()) {
                            is TabOpResult.Rejected -> Toast.makeText(context, r.reason, Toast.LENGTH_LONG).show()
                            is TabOpResult.Ok -> if (r.warnings.isNotEmpty()) pendingNewWarnings = r.warnings
                            null -> GhostService.startBrowser(context)
                        }
                    }
                }) { Icon(Icons.Outlined.Add, contentDescription = "New tab") }
                IconButton(onClick = { showSwitcher = true }) {
                    Icon(Icons.Outlined.GridView, contentDescription = "Switcher")
                }
                val master = session?.masterMuted == true
                IconButton(onClick = { scope.launch { tm?.setMasterMuted(!master) } }) {
                    Icon(
                        if (master) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                        contentDescription = "Master mute",
                    )
                }
                Text("${tabs.size} tabs", color = GhostMuted)
                if (active?.audioPlaying == true) Text("♪ playing", color = GhostMint)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LiveSurface()
            }
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inject,
                    onValueChange = { inject = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Type into page") },
                )
                TextButton(onClick = { tm?.injectText(inject) }) { Text("Insert") }
            }
            active?.let { tab ->
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mute tab", modifier = Modifier.weight(1f))
                    Switch(checked = tab.muted, onCheckedChange = { v -> scope.launch { tm?.setMuted(tab.id, v) } })
                }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep-visible shim (best-effort)", modifier = Modifier.weight(1f))
                    Switch(checked = tab.keepVisibleShim, onCheckedChange = { v -> scope.launch { tm?.setKeepVisibleShim(tab.id, v) } })
                }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lite mode", modifier = Modifier.weight(1f))
                    Switch(checked = tab.liteMode, onCheckedChange = { v -> scope.launch { tm?.setLiteMode(tab.id, v) } })
                }
                VisibilityShimInjector.drmWarningForUrl(tab.url)?.let {
                    Text(it, color = GhostWarn, modifier = Modifier.padding(8.dp))
                }
                if (tab.liveStatus == TabLiveStatus.PAUSED_BY_SYSTEM) {
                    Button(
                        onClick = { scope.launch { tm?.resume(tab.id) } },
                        modifier = Modifier.padding(8.dp),
                    ) { Text("Paused by system — tap to resume") }
                }
            }
            notices.take(2).forEach { Text(it, color = GhostMuted, modifier = Modifier.padding(horizontal = 8.dp)) }
        }
    }

    if (showSwitcher) {
        AlertDialog(
            onDismissRequest = { showSwitcher = false },
            confirmButton = { TextButton(onClick = { showSwitcher = false }) { Text("Close") } },
            title = { Text("All tabs") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(360.dp)) {
                    items(tabs, key = { it.id }) { tab ->
                        Card(
                            modifier = Modifier
                                .padding(4.dp)
                                .combinedClickable(
                                    onClick = {
                                        scope.launch { tm?.select(tab.id) }
                                        showSwitcher = false
                                    },
                                    onLongClick = { longPressTab = tab },
                                ),
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(tab.displayTitle, maxLines = 2)
                                Text(UrlNormalizer.hostOf(tab.url), color = GhostMuted, maxLines = 1)
                                Text(
                                    when {
                                        tab.liveStatus == TabLiveStatus.PAUSED_BY_SYSTEM -> "paused by system"
                                        tab.audioPlaying -> "live · audio"
                                        else -> "live"
                                    },
                                    color = if (tab.liveStatus == TabLiveStatus.PAUSED_BY_SYSTEM) GhostWarn else GhostMint,
                                )
                            }
                        }
                    }
                }
            },
        )
    }

    pendingClose?.let { tab ->
        AlertDialog(
            onDismissRequest = { pendingClose = null },
            title = { Text("Close this tab?") },
            text = {
                Text(
                    "“${tab.displayTitle}” will be destroyed. This is the only way a tab disappears. The other tabs keep running.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        tm?.closeTab(tab.id, userConfirmed = true, closePinnedExplicit = tab.pinned)
                        pendingClose = null
                    }
                }) { Text("Close tab") }
            },
            dismissButton = { TextButton(onClick = { pendingClose = null }) { Text("Keep") } },
        )
    }

    pendingNewWarnings?.let { w ->
        AlertDialog(
            onDismissRequest = { pendingNewWarnings = null },
            title = { Text("Tab created") },
            text = { Text(w.joinToString("\n\n")) },
            confirmButton = { TextButton(onClick = { pendingNewWarnings = null }) { Text("OK") } },
        )
    }

    longPressTab?.let { tab ->
        AlertDialog(
            onDismissRequest = { longPressTab = null },
            title = { Text(tab.displayTitle) },
            text = {
                Column {
                    TextButton(onClick = {
                        scope.launch { tm?.setPinned(tab.id, !tab.pinned) }
                        longPressTab = null
                    }) { Text(if (tab.pinned) "Unpin" else "Pin (won’t close accidentally)") }
                    TextButton(onClick = {
                        scope.launch { tm?.duplicate(tab.id) }
                        longPressTab = null
                    }) { Text("Duplicate") }
                    TextButton(onClick = {
                        scope.launch { tm?.setMuted(tab.id, !tab.muted) }
                        longPressTab = null
                    }) { Text(if (tab.muted) "Unmute" else "Mute") }
                    TextButton(onClick = {
                        scope.launch { tm?.closeOthers(tab.id, userConfirmed = true) }
                        longPressTab = null
                    }) { Text("Close others (pinned are kept)") }
                }
            },
            confirmButton = { TextButton(onClick = { longPressTab = null }) { Text("Done") } },
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Tab history log") },
            text = {
                Text(
                    "Created / last active / last restored are on each tab. GhostDisplay never silently closes a tab — if the OS killed the process, restore events appear here after relaunch.\n\n" +
                        tabs.joinToString("\n\n") { t ->
                            "• ${t.displayTitle}\n  created ${t.createdAt}\n  last active ${t.lastActiveAt}\n  last restored ${t.lastRestoredAt ?: "never"}\n  ${t.liveStatus}"
                        },
                )
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun MemoryBar(mem: MemorySnapshot?) {
    if (mem == null) return
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        LinearProgressIndicator(
            progress = mem.fraction,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (mem.lowMemory) GhostDanger else GhostMint,
        )
        Text(mem.label, color = GhostMuted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStrip(
    tabs: List<TabRecord>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onCloseRequest: (TabRecord) -> Unit,
    onLongPress: (TabRecord) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == activeId
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) GhostMint.copy(alpha = 0.2f) else GhostSurface)
                    .combinedClickable(
                        onClick = {
                            if (tab.liveStatus == TabLiveStatus.PAUSED_BY_SYSTEM) {
                                onSelect(tab.id)
                            } else onSelect(tab.id)
                        },
                        onLongClick = { onLongPress(tab) },
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab.pinned) Icon(Icons.Outlined.PushPin, contentDescription = "Pinned", modifier = Modifier.size(12.dp), tint = GhostMint)
                if (tab.audioPlaying) Text("♪ ", color = GhostMint)
                Text(tab.displayTitle.take(18), color = if (selected) GhostMint else Color.White)
                IconButton(onClick = { onCloseRequest(tab) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveSurface() {
    val host = BrowserRuntime.host
    val engine = GhostService.instance?.engine()
    val sessionId = BrowserRuntime.displaySessionId
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        if (sessionId != null) engine?.setSurface(sessionId, holder.surface)
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        if (sessionId != null) engine?.restoreEngineSurface(sessionId)
                    }
                })
                setOnTouchListener { v, ev ->
                    val tm = BrowserRuntime.tabManager ?: return@setOnTouchListener false
                    val (dw, dh) = host?.contentSize() ?: (v.width to v.height)
                    val mapped = CoordinateMapper.canvasToDisplay(
                        ev.x, ev.y, v.width.toFloat(), v.height.toFloat(),
                        dw.coerceAtLeast(1), dh.coerceAtLeast(1),
                    )
                    val action = when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> MotionEvent.ACTION_DOWN
                        MotionEvent.ACTION_MOVE -> MotionEvent.ACTION_MOVE
                        MotionEvent.ACTION_UP -> MotionEvent.ACTION_UP
                        MotionEvent.ACTION_CANCEL -> MotionEvent.ACTION_CANCEL
                        else -> return@setOnTouchListener false
                    }
                    tm.dispatchTouch(action, mapped.x, mapped.y)
                    true
                }
            }
        },
    )
}
