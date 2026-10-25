package com.ghostdisplay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.display.DisplayConfig
import com.ghostdisplay.app.launch.KnownLimits
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostWarn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppSettingsScreen(
    app: LaunchableApp,
    profile: AppProfile,
    onBack: () -> Unit,
    onSave: (AppProfile) -> Unit,
    onRun: (AppProfile) -> Unit,
) {
    var width by remember { mutableStateOf(profile.widthPx?.toString().orEmpty()) }
    var height by remember { mutableStateOf(profile.heightPx?.toString().orEmpty()) }
    var dpi by remember { mutableStateOf(profile.densityDpi?.toString().orEmpty()) }
    var orientation by remember { mutableStateOf(profile.orientationEnum()) }
    var surface by remember { mutableStateOf(profile.surfaceModeEnum()) }
    var secure by remember { mutableStateOf(profile.secure) }
    var audio by remember { mutableStateOf(profile.audioEnabled) }
    var blockNotif by remember { mutableStateOf(profile.blockNotifications) }
    val limit = KnownLimits.forApp(app.packageName, app.label)

    fun current(): AppProfile = profile.copy(
        widthPx = width.toIntOrNull(),
        heightPx = height.toIntOrNull(),
        densityDpi = dpi.toIntOrNull(),
        orientation = orientation.name,
        surfaceMode = surface.name,
        secure = secure,
        audioEnabled = audio,
        blockNotifications = blockNotif,
        updatedAt = System.currentTimeMillis(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(app.packageName, color = GhostMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Compatibility: ", style = MaterialTheme.typography.titleMedium)
                CompatChip(profile.compatibilityEnum())
            }
            if (limit != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Known limit", color = GhostWarn)
                        Text(limit)
                    }
                }
            }
            if (profile.lastError != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Last launch", color = GhostWarn)
                        Text(profile.lastError)
                    }
                }
            }

            Text("Resolution (empty = match physical display)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(width, { width = it.filter { ch -> ch.isDigit() } }, label = { Text("Width") }, modifier = Modifier.weight(1f))
                OutlinedTextField(height, { height = it.filter { ch -> ch.isDigit() } }, label = { Text("Height") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(dpi, { dpi = it.filter { ch -> ch.isDigit() } }, label = { Text("DPI") }, modifier = Modifier.fillMaxWidth())

            Text("Orientation", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DisplayConfig.Orientation.entries.forEach { o ->
                    FilterChip(
                        selected = orientation == o,
                        onClick = { orientation = o },
                        label = { Text(o.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    )
                }
            }

            Text("Surface mode", style = MaterialTheme.typography.titleMedium)
            Text(
                "ImageReader is lighter. MediaProjection is required for video, many games, secure surfaces, and audio routing.",
                color = GhostMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = surface == DisplayConfig.SurfaceMode.IMAGE_READER,
                    onClick = { surface = DisplayConfig.SurfaceMode.IMAGE_READER },
                    label = { Text("ImageReader") },
                )
                FilterChip(
                    selected = surface == DisplayConfig.SurfaceMode.MEDIA_PROJECTION,
                    onClick = { surface = DisplayConfig.SurfaceMode.MEDIA_PROJECTION },
                    label = { Text("MediaProjection") },
                )
            }

            SettingToggle("Secure display", "Adds VIRTUAL_DISPLAY_FLAG_SECURE. Needed by some DRM apps; may still black-screen.", secure) { secure = it }
            SettingToggle("Audio routing", "Capture the hidden app’s playback and play it on speaker / Bluetooth. Needs MediaProjection + RECORD_AUDIO. Continues with the screen off.", audio) { audio = it }
            SettingToggle("Block notifications", "Requires the optional Notification listener in Setup. Drops shade notifications from this package while it is hidden.", blockNotif) { blockNotif = it }

            Button(onClick = { onSave(current()) }, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
            Button(onClick = { onRun(current()) }, modifier = Modifier.fillMaxWidth()) { Text("Save & run hidden") }
        }
    }
}

@Composable
private fun SettingToggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = GhostMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
