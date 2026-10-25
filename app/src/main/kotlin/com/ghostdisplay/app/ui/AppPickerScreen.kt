package com.ghostdisplay.app.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.Compatibility
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.launch.CompatibilityDetector
import com.ghostdisplay.app.launch.KnownLimits
import com.ghostdisplay.app.ui.theme.GhostDanger
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostWarn

data class PickerRow(
    val app: LaunchableApp,
    val profile: AppProfile?,
    val icon: Drawable?,
)

@Composable
fun AppPickerScreen(
    rows: List<PickerRow>,
    query: String,
    onQuery: (String) -> Unit,
    onRunHidden: (LaunchableApp) -> Unit,
    onSettings: (LaunchableApp) -> Unit,
    liveByPackage: Map<String, GhostSession> = emptyMap(),
    onOpenViewer: (GhostSession) -> Unit = {},
    searchPlaceholder: String = "Search apps",
    intro: String = "",
    emptyMessage: String = "No matching apps.",
) {
    val filtered = remember(rows, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) rows
        else rows.filter {
            it.app.label.lowercase().contains(q) || it.app.packageName.lowercase().contains(q)
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(searchPlaceholder) },
            singleLine = true,
        )
        if (intro.isNotBlank()) {
            Text(
                intro,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = GhostMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        emptyMessage,
                        modifier = Modifier.padding(16.dp),
                        color = GhostMuted,
                    )
                }
            }
            items(filtered, key = { it.app.packageName }) { row ->
                AppRow(
                    row = row,
                    live = liveByPackage[row.app.packageName],
                    onRunHidden = onRunHidden,
                    onSettings = onSettings,
                    onOpenViewer = onOpenViewer,
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    row: PickerRow,
    live: GhostSession?,
    onRunHidden: (LaunchableApp) -> Unit,
    onSettings: (LaunchableApp) -> Unit,
    onOpenViewer: (GhostSession) -> Unit,
) {
    val compat = row.profile?.compatibilityEnum() ?: Compatibility.UNKNOWN
    val limit = KnownLimits.forApp(row.app.packageName, row.app.label)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                if (live != null) onOpenViewer(live) else onSettings(row.app)
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bmp = remember(row.app.packageName) {
            row.icon?.safeImageBitmap(96)
        }
        Box(Modifier.size(44.dp)) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            if (live != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .align(Alignment.TopEnd)
                        .background(GhostMint, CircleShape),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(row.app.label, style = MaterialTheme.typography.titleMedium)
            Text(row.app.packageName, color = GhostMuted, style = MaterialTheme.typography.bodyMedium)
            if (live != null) {
                Text(
                    "Running hidden · ${live.tier}",
                    color = GhostMint,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompatChip(compat)
                if (limit != null) {
                    Text(KnownLimits.categoryOf(row.app.packageName, row.app.label), color = GhostMuted)
                }
            }
        }
        IconButton(onClick = { onSettings(row.app) }) {
            Icon(Icons.Outlined.Settings, contentDescription = "Per-app settings")
        }
        FilledTonalButton(onClick = { onRunHidden(row.app) }) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text("Run hidden")
        }
    }
}

@Composable
fun CompatChip(compat: Compatibility) {
    val (label, color) = when (compat) {
        Compatibility.SUPPORTED -> CompatibilityDetector.badgeLabel(compat) to GhostMint
        Compatibility.LIMITED -> CompatibilityDetector.badgeLabel(compat) to GhostWarn
        Compatibility.UNSUPPORTED -> CompatibilityDetector.badgeLabel(compat) to GhostDanger
        Compatibility.UNKNOWN -> "Unknown" to GhostMuted
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = color,
        ),
    )
}
