package com.ghostdisplay.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ghostdisplay.app.ui.theme.GhostDanger
import com.ghostdisplay.app.ui.theme.GhostMuted

@Composable
fun WarningScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Read this once", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "A virtual display hides the picture, not the system.",
            style = MaterialTheme.typography.titleLarge,
            color = GhostDanger,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "The app you run hidden can still:\n" +
                "• use the network\n" +
                "• send notifications\n" +
                "• play audio through the speaker or Bluetooth\n" +
                "• change its own settings, sign you in, or make purchases if you remote-control it that far\n\n" +
                "GhostDisplay does not sandbox, VPN, or freeze the hidden app. It only keeps it off the physical screen.\n\n" +
                "Finance apps warn before start (toggle on by default in Setup). A hidden display is not isolation. You can add packages that refuse launch in Setup.\n\n" +
                "GhostDisplay does not record or exfiltrate captured frames. The optional debug preview is shown only when you open it.\n\n" +
                "Hidden windows on this OS need Shizuku (no root). You will also grant ordinary runtime permissions, the Accessibility toggle, and — when a session needs it — the standard screen-capture dialog. Setup has the step-by-step Shizuku guide.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("I understand — continue")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("Don’t use GhostDisplay")
        }
        Spacer(Modifier.height(8.dp))
        Text("Declining exits the app. Nothing has been enabled yet.", color = GhostMuted)
    }
}
