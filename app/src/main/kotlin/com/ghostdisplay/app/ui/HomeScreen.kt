package com.ghostdisplay.app.ui

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostSurface
import com.ghostdisplay.app.ui.theme.GhostSurface2
import com.ghostdisplay.app.util.SetupStatus

private enum class HomeFilter { All, Browsers, Hidden }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    rows: List<PickerRow>,
    query: String,
    onQuery: (String) -> Unit,
    sessions: List<GhostSession>,
    onShowActions: (PickerRow) -> Unit,
    onOpenViewer: (GhostSession) -> Unit,
    onStop: (GhostSession) -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val liveByPackage = remember(sessions) {
        sessions.filter { it.status == SessionStatus.RUNNING }.associateBy { it.packageName }
    }
    val a11yOn = SetupStatus.isAccessibilityEnabled(context)
    val notifOn = SetupStatus.hasNotifications(context)

    var filterValue by remember { mutableStateOf(HomeFilter.All) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val tabStyle = filterValue == HomeFilter.Browsers || filterValue == HomeFilter.Hidden
    val selecting = selectedIds.isNotEmpty()
    LaunchedEffect(filterValue) { selectedIds = emptySet() }

    val filtered = remember(rows, query, filterValue, liveByPackage) {
        val q = query.trim().lowercase()
        rows.asSequence()
            .filter {
                q.isEmpty() ||
                    it.app.label.lowercase().contains(q) ||
                    it.app.packageName.lowercase().contains(q)
            }
            .filter {
                when (filterValue) {
                    HomeFilter.All -> true
                    HomeFilter.Browsers -> isInstalledBrowser(it.app)
                    HomeFilter.Hidden -> liveByPackage.containsKey(it.app.packageName)
                }
            }
            .toList()
    }

    fun hapticHold(row: PickerRow) {
        runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        if (tabStyle) {
            val id = row.app.packageName
            selectedIds = if (selecting) {
                if (id in selectedIds) selectedIds - id else selectedIds + id
            } else setOf(id)
        } else {
            onShowActions(row)
        }
    }

    Column(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = if (tabStyle) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 84.dp),
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GhostDisplay", style = MaterialTheme.typography.headlineMedium, color = GhostMint)
                        Text(
                            if (tabStyle) "Hold a tab to select it"
                            else "Hold an app to run it hidden",
                            color = GhostMuted,
                        )
                    }
                    IconButton(onClick = onOpenSessions) {
                        Icon(Icons.Outlined.Layers, contentDescription = "Active sessions")
                    }
                    IconButton(onClick = onOpenSetup) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Setup")
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!a11yOn || !notifOn) {
                    SetupNudgeCard(
                        accessibilityOn = a11yOn,
                        notificationsOn = notifOn,
                        onOpenSetup = onOpenSetup,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text(if (tabStyle) "Search tabs" else "Search apps") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterValue == HomeFilter.All,
                        onClick = { filterValue = HomeFilter.All },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = filterValue == HomeFilter.Browsers,
                        onClick = { filterValue = HomeFilter.Browsers },
                        label = { Text("Browsers") },
                    )
                    FilterChip(
                        selected = filterValue == HomeFilter.Hidden,
                        onClick = { filterValue = HomeFilter.Hidden },
                        label = { Text("Hidden") },
                    )
                }
                if (liveByPackage.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Running hidden", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        liveByPackage.values.forEach { session ->
                            val row = rows.firstOrNull { it.app.packageName == session.packageName }
                            LiveChip(
                                label = session.appLabel,
                                onClick = { onOpenViewer(session) },
                                onLongClick = {
                                    if (row != null) hapticHold(row)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = GhostMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (tabStyle) "Hold a tab for options, then tap more to select. Tap a tab to open it."
                        else if (a11yOn) "Hold any app here — or on your home screen — for Run hidden / Stop."
                        else "Hold any app here for Run hidden / Stop. Enable Accessibility in Setup to do the same on the home screen.",
                        color = GhostMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (filterValue == HomeFilter.Browsers) "No installed browser found. Install Chrome, then it will show up here."
                    else if (filterValue == HomeFilter.Hidden) "Nothing is running hidden. Hold an app to start."
                    else "No matching apps.",
                    color = GhostMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(filtered, key = { it.app.packageName }) { row ->
            val live = liveByPackage[row.app.packageName]
            val id = row.app.packageName
            if (tabStyle) {
                TabCard(
                    card = SwitcherCard(
                        id = id,
                        title = row.app.label,
                        subtitle = if (live != null) "Running hidden · ${live.tier}" else row.app.packageName,
                        live = live != null,
                        icon = row.icon,
                    ),
                    selected = id in selectedIds,
                    selecting = selecting,
                    onOpen = {
                        when {
                            selecting -> {
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            }
                            live != null -> onOpenViewer(live)
                            else -> onShowActions(row)
                        }
                    },
                    onLongPress = { hapticHold(row) },
                    onClose = live?.let { session ->
                        {
                            onStop(session)
                        }
                    },
                )
            } else {
                AppGridCell(
                    row = row,
                    live = live,
                    onClick = {
                        if (live != null) onOpenViewer(live) else onShowActions(row)
                    },
                    onLongClick = { hapticHold(row) },
                )
            }
        }
    }
    if (selecting) {
        val selectedRows = filtered.filter { it.app.packageName in selectedIds }
        val liveSelected = selectedRows.mapNotNull { liveByPackage[it.app.packageName] }
        TabSelectBar(
            count = selectedIds.size,
            onDone = { selectedIds = emptySet() },
            onOpen = liveSelected.singleOrNull()?.let { session ->
                {
                    selectedIds = emptySet()
                    onOpenViewer(session)
                }
            },
            onCloseSelected = if (liveSelected.isNotEmpty()) {
                {
                    liveSelected.forEach(onStop)
                    selectedIds = emptySet()
                }
            } else if (selectedRows.size == 1) {
                {
                    val row = selectedRows.first()
                    selectedIds = emptySet()
                    onShowActions(row)
                }
            } else null,
            closeLabel = if (liveSelected.isNotEmpty()) "Stop hidden" else "Run hidden",
        )
    }
    }
}

@Composable
private fun SetupNudgeCard(
    accessibilityOn: Boolean,
    notificationsOn: Boolean,
    onOpenSetup: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    Card(
        colors = CardDefaults.cardColors(containerColor = GhostSurface2),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("One-time setup", style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    if (!accessibilityOn) append("Turn on Accessibility so you can hold an app on the home screen to run or stop it hidden. ")
                    if (!notificationsOn) append("Allow notifications for the Stop action while something is hidden.")
                }.trim(),
                color = GhostMuted,
            )
            Row {
                Button(onClick = {
                    if (!accessibilityOn) {
                        activity?.startActivity(SetupStatus.accessibilitySettingsIntent())
                    } else {
                        onOpenSetup()
                    }
                }) { Text(if (!accessibilityOn) "Open Accessibility" else "Open Setup") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpenSetup) { Text("All grants") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GhostSurface2)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(GhostMint, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridCell(
    row: PickerRow,
    live: GhostSession?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bmp = remember(row.app.packageName, row.icon) { row.icon?.safeImageBitmap(128) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .then(
                    if (live != null) Modifier.border(2.dp, GhostMint, RoundedCornerShape(16.dp))
                    else Modifier,
                )
                .clip(RoundedCornerShape(16.dp))
                .background(GhostSurface),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = row.app.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
            if (live != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .background(GhostMint, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            row.app.label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (live != null) GhostMint else MaterialTheme.colorScheme.onBackground,
        )
    }
}
