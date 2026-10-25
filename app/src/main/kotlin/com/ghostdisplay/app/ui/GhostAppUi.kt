package com.ghostdisplay.app.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.service.GhostService
import kotlinx.coroutines.launch

private enum class Dest { Home, Sessions, Setup }

@Composable
fun GhostAppUi(vm: GhostViewModel) {
    val onboarding by vm.onboardingComplete.collectAsState()
    val warning by vm.warningAccepted.collectAsState()
    if (!onboarding) {
        OnboardingScreen(onFinished = { vm.completeOnboarding() })
        return
    }
    if (!warning) {
        val activity = LocalContext.current as Activity
        WarningScreen(
            onAccept = { vm.acceptWarning() },
            onDecline = { activity.finish() },
        )
        return
    }
    MainShell(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(vm: GhostViewModel) {
    var dest by remember { mutableStateOf(Dest.Home) }
    var settingsPkg by remember { mutableStateOf<String?>(null) }
    var actionRow by remember { mutableStateOf<PickerRow?>(null) }
    val rows by vm.rows.collectAsState()
    val query by vm.query.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settingsApp = settingsPkg?.let { vm.findApp(it) }
    if (settingsApp != null) {
        var loaded by remember(settingsApp.packageName) { mutableStateOf<AppProfile?>(null) }
        LaunchedEffect(settingsApp.packageName) {
            loaded = vm.profileOf(settingsApp.packageName)
        }
        val profile = loaded
        if (profile != null) {
            LaunchGate(vm) { gate ->
                PerAppSettingsScreen(
                    app = settingsApp,
                    profile = profile,
                    onBack = { settingsPkg = null },
                    onSave = {
                        vm.saveProfile(it)
                        settingsPkg = null
                    },
                    onRun = {
                        vm.saveProfile(it)
                        settingsPkg = null
                        gate.requestLaunch(settingsApp, it)
                    },
                )
            }
            return
        }
    }

    LaunchGate(vm) { gate ->
        Box(Modifier.fillMaxSize()) {
        when (dest) {
            Dest.Sessions -> Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Active sessions") },
                        navigationIcon = {
                            IconButton(onClick = { dest = Dest.Home }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    ActiveSessionsScreen(
                        sessions = sessions,
                        onRemote = gate.openRemote,
                        onPreview = gate.openViewer,
                        onStop = gate.stop,
                        onStopAll = gate.stopAll,
                        onMute = { s, muted ->
                            val i = Intent(context, GhostService::class.java)
                                .setAction(GhostService.ACTION_MUTE)
                                .putExtra(GhostService.EXTRA_SESSION_ID, s.id)
                                .putExtra(GhostService.EXTRA_MUTED, muted)
                            context.startService(i)
                        },
                    )
                }
            }
            Dest.Setup -> Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Setup") },
                        navigationIcon = {
                            IconButton(onClick = { dest = Dest.Home }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    SetupScreen()
                }
            }
            Dest.Home -> Scaffold { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    HomeScreen(
                        rows = rows,
                        query = query,
                        onQuery = vm::setQuery,
                        sessions = sessions,
                        onShowActions = { actionRow = it },
                        onOpenViewer = gate.openViewer,
                        onStop = gate.stop,
                        onOpenSessions = { dest = Dest.Sessions },
                        onOpenSetup = { dest = Dest.Setup },
                    )
                }
            }
        }

        val sheetRow = actionRow
        if (sheetRow != null) {
            val live = sessions.firstOrNull {
                it.packageName == sheetRow.app.packageName && it.status == SessionStatus.RUNNING
            }
            AppActionSheet(
                target = AppActionTarget(sheetRow.app, sheetRow.icon, live),
                onDismiss = { actionRow = null },
                onRunHidden = { app ->
                    actionRow = null
                    scope.launch {
                        val p = vm.profileOf(app.packageName)
                        gate.requestLaunch(app, p)
                    }
                },
                onStopHidden = gate.stop,
                onOpenViewer = gate.openViewer,
                onSettings = { settingsPkg = it.packageName },
            )
        }
        }
    }
}
