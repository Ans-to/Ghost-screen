package com.ghostdisplay.app.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import com.ghostdisplay.app.GhostApp
import com.ghostdisplay.app.data.BlockedApp
import com.ghostdisplay.app.launch.KnownLimits
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostWarn
import com.ghostdisplay.app.util.SetupStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val app = context.applicationContext as GhostApp
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(SetupStatus.items(context)) }
    val restoreBoot by app.container.prefs.restoreOnBoot.collectAsState(initial = false)
    val restoreDeath by app.container.prefs.restoreOnProcessDeath.collectAsState(initial = true)
    val financeWarn by app.container.prefs.financeWarn.collectAsState(initial = true)
    val overlayDot by app.container.prefs.overlayDot.collectAsState(initial = false)
    val blocked by app.container.db.blocklist().observeAll().collectAsState(initial = emptyList())
    var newBlocked by remember { mutableStateOf("") }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { items = SetupStatus.items(context) }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { items = SetupStatus.items(context) }

    LaunchedEffect(Unit) {
        while (true) {
            items = SetupStatus.items(context)
            delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "One-time grants. After this you start and stop by holding an app. GhostDisplay never asks for root, Shizuku, ADB, or a second app.",
            color = GhostMuted,
        )
        items.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (item.granted) GhostMint else GhostWarn,
                        )
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (item.granted) "On" else if (item.required) "Required" else "Optional",
                                color = if (item.granted) GhostMint else GhostWarn,
                            )
                        }
                    }
                    Text(item.body, style = MaterialTheme.typography.bodyMedium)
                    if (!item.granted && item.id != "mediaprojection") {
                        Button(onClick = {
                            when (item.id) {
                                "accessibility" -> activity.startActivity(SetupStatus.accessibilitySettingsIntent())
                                "battery" -> activity.startActivity(SetupStatus.batteryIntent(context))
                                "notifications" -> {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        activity.startActivity(SetupStatus.appNotificationSettings(context))
                                    }
                                }
                                "audio" -> audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                "listener" -> activity.startActivity(SetupStatus.notificationListenerIntent())
                                "usage" -> activity.startActivity(SetupStatus.usageSettingsIntent())
                                "overlay" -> activity.startActivity(SetupStatus.overlayIntent(context))
                            }
                        }) { Text(item.actionLabel) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keep-alive", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Offer restore after reboot")
                        Text(
                            "After reboot, hidden Chrome (and other apps) cannot auto-relaunch: screen-capture consent does not survive reboot. You get a notification to start the hidden window again.",
                            color = GhostMuted,
                        )
                    }
                    Switch(
                        checked = restoreBoot,
                        onCheckedChange = { v -> scope.launch { app.container.prefs.setRestoreOnBoot(v) } },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Offer restore after process death")
                    }
                    Switch(
                        checked = restoreDeath,
                        onCheckedChange = { v -> scope.launch { app.container.prefs.setRestoreOnProcessDeath(v) } },
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Return indicator", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Silent grouped notifications are on whenever a hidden window is live (importance min, no sound). The 8dp overlay dot is off until you opt in. It only appears while that real app is in the foreground on the main display — never over home, GhostDisplay, the Viewer, or a different app. Long-press hides it for the rest of the day.",
                    color = GhostMuted,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("8dp overlay return dot")
                        Text("Requires Draw over other apps. Not a floating app window.", color = GhostMuted)
                    }
                    Switch(
                        checked = overlayDot,
                        onCheckedChange = { v ->
                            scope.launch {
                                app.container.prefs.setOverlayDot(v)
                                com.ghostdisplay.app.indicator.OverlayDotService.sync(context, v)
                                if (v && !android.provider.Settings.canDrawOverlays(context)) {
                                    activity.startActivity(SetupStatus.overlayIntent(context))
                                }
                            }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Warn before finance apps")
                        Text(
                            "On by default. A hidden display is not isolation. This warns; it does not hard-stop. User-added packages below still refuse launch.",
                            color = GhostMuted,
                        )
                    }
                    Switch(
                        checked = financeWarn,
                        onCheckedChange = { v -> scope.launch { app.container.prefs.setFinanceWarn(v) } },
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("User blocklist", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Packages you add here refuse “Run hidden”. Default finance packages are a warning (toggle above), not a hard stop. A virtual display hides the picture, not the system.",
                    color = GhostMuted,
                )
                blocked.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.packageName)
                            Text(if (b.isDefault) "Finance warning list" else b.reason, color = GhostMuted)
                        }
                        if (!b.isDefault) {
                            TextButton(onClick = {
                                scope.launch { app.container.db.blocklist().removeUser(b.packageName) }
                            }) { Text("Remove") }
                        }
                    }
                }
                OutlinedTextField(
                    value = newBlocked,
                    onValueChange = { newBlocked = it },
                    label = { Text("Add package name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = {
                    val pkg = newBlocked.trim()
                    if (pkg.isNotEmpty()) {
                        scope.launch {
                            app.container.db.blocklist().upsert(
                                BlockedApp(pkg, "User blocklist", isDefault = false),
                            )
                        }
                        newBlocked = ""
                    }
                }) { Text("Block package") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Compatibility matrix", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Runtime detection always wins. This is what we typically see, not a guarantee.",
                    color = GhostMuted,
                )
                KnownLimits.matrix.forEach { (cat, result, notes) ->
                    Text("$cat — $result", style = MaterialTheme.typography.titleMedium)
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = {
            scope.launch {
                app.container.prefs.setOnboardingComplete(false)
                app.container.prefs.setWarningAccepted(false)
            }
        }) { Text("Replay onboarding") }
    }
}
