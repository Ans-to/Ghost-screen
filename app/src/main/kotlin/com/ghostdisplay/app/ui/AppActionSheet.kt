package com.ghostdisplay.app.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.launch.KnownLimits
import com.ghostdisplay.app.ui.theme.GhostDanger
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted

data class AppActionTarget(
    val app: LaunchableApp,
    val icon: Drawable?,
    val live: GhostSession?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionSheet(
    target: AppActionTarget,
    onDismiss: () -> Unit,
    onRunHidden: (LaunchableApp) -> Unit,
    onStopHidden: (GhostSession) -> Unit,
    onOpenViewer: (GhostSession) -> Unit,
    onSettings: ((LaunchableApp) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AppActionBody(
            target = target,
            onDismiss = onDismiss,
            onRunHidden = onRunHidden,
            onStopHidden = onStopHidden,
            onOpenViewer = onOpenViewer,
            onSettings = onSettings,
        )
    }
}

@Composable
fun AppActionBody(
    target: AppActionTarget,
    onDismiss: () -> Unit,
    onRunHidden: (LaunchableApp) -> Unit,
    onStopHidden: (GhostSession) -> Unit,
    onOpenViewer: (GhostSession) -> Unit,
    onSettings: ((LaunchableApp) -> Unit)? = null,
) {
    val app = target.app
    val live = target.live
    val bmp = remember(app.packageName) { target.icon?.toBitmap(128, 128) }
    val limit = KnownLimits.forApp(app.packageName, app.label)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp)) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
                if (live != null) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .background(GhostMint, CircleShape),
                    )
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (live != null) "Running hidden · ${live.tier}" else "Hold to run or stop a hidden window",
                    color = if (live != null) GhostMint else GhostMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (limit != null) {
            Text(limit, color = GhostMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        if (live != null) {
            Button(
                onClick = {
                    onStopHidden(live)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GhostDanger),
            ) {
                Icon(Icons.Outlined.StopCircle, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Stop hidden")
            }
            FilledTonalButton(
                onClick = {
                    onOpenViewer(live)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Visibility, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Open viewer")
            }
        } else {
            Button(
                onClick = { onRunHidden(app) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Run hidden")
            }
        }
        if (onSettings != null) {
            OutlinedButton(
                onClick = {
                    onSettings(app)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Settings")
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
