package com.ghostdisplay.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
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
import com.ghostdisplay.app.GhostApp
import com.ghostdisplay.app.control.RemoteControlActivity
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.display.DisplayConfig
import com.ghostdisplay.app.profile.AppCategory
import com.ghostdisplay.app.profile.AppCategoryClassifier
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.service.GhostStopReceiver
import com.ghostdisplay.app.service.MediaProjectionHolder
import com.ghostdisplay.app.session.AppViewerActivity
import kotlinx.coroutines.launch

private enum class Tab { Browser, Apps, Sessions, Setup }

private data class PendingStart(
    val app: LaunchableApp,
    val profile: AppProfile?,
    val additional: Boolean,
    val financeConfirmed: Boolean,
)

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
    MainTabs(vm)
}

@Composable
private fun MainTabs(vm: GhostViewModel) {
    var tab by remember { mutableStateOf(Tab.Browser) }
    val browserRows = remember(rows) { rows.filter { isInstalledBrowser(it.app) } }
    var settingsPkg by remember { mutableStateOf<String?>(null) }
    val rows by vm.rows.collectAsState()
    val query by vm.query.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ghostApp = context.applicationContext as GhostApp
    val financeOn by ghostApp.container.prefs.financeWarn.collectAsState(initial = true)
    val explainerSeen by ghostApp.container.prefs.sessionExplainerSeen.collectAsState(initial = false)
    var pendingLaunch by remember { mutableStateOf<PendingStart?>(null) }
    var pendingExisting by remember { mutableStateOf<Pair<LaunchableApp, GhostSession>?>(null) }
    var pendingFinance by remember { mutableStateOf<Pair<LaunchableApp, AppProfile?>?>(null) }
    var showExplainer by remember { mutableStateOf(false) }
    var explainerApp by remember { mutableStateOf<Pair<LaunchableApp, AppProfile?>?>(null) }
    var explainerDone by remember { mutableStateOf(false) }

    val mpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pending = pendingLaunch
        pendingLaunch = null
        if (pending == null) return@rememberLauncherForActivityResult
        val hardNeed = pending.profile?.surfaceModeEnum() == DisplayConfig.SurfaceMode.MEDIA_PROJECTION ||
            pending.profile?.secure == true
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            GhostService.startHidden(
                context,
                pending.app.packageName,
                pending.app.activityName,
                pending.app.label,
                result.resultCode,
                result.data,
                additionalWindow = pending.additional,
                financeConfirmed = pending.financeConfirmed,
            )
            tab = Tab.Sessions
        } else if (hardNeed) {
            Toast.makeText(
                context,
                "Screen-capture consent was denied. This profile requires MediaProjection (video/secure). Nothing was recorded, and the app was not launched.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            Toast.makeText(
                context,
                "Screen-capture denied. Launching without MediaProjection — audio routing and some video apps will not work. This is reported on the session card.",
                Toast.LENGTH_LONG,
            ).show()
            GhostService.startHidden(
                context,
                pending.app.packageName,
                pending.app.activityName,
                pending.app.label,
                additionalWindow = pending.additional,
                financeConfirmed = pending.financeConfirmed,
            )
            tab = Tab.Sessions
        }
    }

    fun startNow(app: LaunchableApp, profile: AppProfile?, additional: Boolean, financeConfirmed: Boolean) {
        val needsMp = profile?.surfaceModeEnum() == DisplayConfig.SurfaceMode.MEDIA_PROJECTION ||
            profile?.secure == true ||
            profile?.audioEnabled != false
        if (needsMp && !MediaProjectionHolder.hasConsent()) {
            pendingLaunch = PendingStart(app, profile, additional, financeConfirmed)
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mpLauncher.launch(mgr.createScreenCaptureIntent())
        } else {
            GhostService.startHidden(
                context,
                app.packageName,
                app.activityName,
                app.label,
                additionalWindow = additional,
                financeConfirmed = financeConfirmed,
            )
            tab = Tab.Sessions
        }
    }

    fun openViewer(session: GhostSession) {
        context.startActivity(
            Intent(context, AppViewerActivity::class.java)
                .putExtra(AppViewerActivity.EXTRA_SESSION_ID, session.id),
        )
    }

    fun requestLaunch(app: LaunchableApp, profile: AppProfile?, additional: Boolean = false, financeConfirmed: Boolean = false) {
        val live = sessions.firstOrNull {
            it.packageName == app.packageName && it.status == SessionStatus.RUNNING
        }
        if (live != null && !additional) {
            pendingExisting = app to live
            return
        }
        val category = AppCategoryClassifier.classify(
            app.packageName, app.label, app.requestedOrientation, false,
        )
        if (!explainerSeen && !explainerDone) {
            explainerApp = app to profile
            showExplainer = true
            return
        }
        if (category == AppCategory.FINANCE && financeOn && !financeConfirmed) {
            pendingFinance = app to profile
            return
        }
        startNow(app, profile, additional, financeConfirmed)
    }

    val settingsApp = settingsPkg?.let { vm.findApp(it) }
    if (settingsApp != null) {
        var loaded by remember(settingsApp.packageName) { mutableStateOf<AppProfile?>(null) }
        LaunchedEffect(settingsApp.packageName) {
            loaded = vm.profileOf(settingsApp.packageName)
        }
        val profile = loaded
        if (profile != null) {
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
                    requestLaunch(settingsApp, it)
                },
            )
            return
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Browser,
                    onClick = { tab = Tab.Browser },
                    icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                    label = { Text("Browsers") },
                )
                NavigationBarItem(
                    selected = tab == Tab.Apps,
                    onClick = { tab = Tab.Apps },
                    icon = { Icon(Icons.Outlined.Apps, contentDescription = null) },
                    label = { Text("Apps") },
                )
                NavigationBarItem(
                    selected = tab == Tab.Sessions,
                    onClick = { tab = Tab.Sessions },
                    icon = { Icon(Icons.Outlined.Layers, contentDescription = null) },
                    label = { Text("Sessions") },
                )
                NavigationBarItem(
                    selected = tab == Tab.Setup,
                    onClick = { tab = Tab.Setup },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("Setup") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Browser -> AppPickerScreen(
                    rows = browserRows,
                    query = query,
                    onQuery = vm::setQuery,
                    onRunHidden = { app ->
                        scope.launch {
                            val p = vm.profileOf(app.packageName)
                            requestLaunch(app, p)
                        }
                    },
                    onSettings = { settingsPkg = it.packageName },
                    liveByPackage = sessions.filter { it.status == SessionStatus.RUNNING }
                        .associateBy { it.packageName },
                    onOpenViewer = { openViewer(it) },
                    searchPlaceholder = "Search browsers",
                    intro = "This is your real installed browser — Chrome, Firefox, or whatever you have — same tabs, same account, same data. Run hidden puts it on a live virtual display so it is not backgrounded. Open at least 10 tabs in the Viewer. Closing the Viewer does not stop the browser.",
                    emptyMessage = "No installed browser found. Install Chrome, then it will show up here.",
                )
                Tab.Apps -> AppPickerScreen(
                    rows = rows,
                    query = query,
                    onQuery = vm::setQuery,
                    onRunHidden = { app ->
                        scope.launch {
                            val p = vm.profileOf(app.packageName)
                            requestLaunch(app, p)
                        }
                    },
                    onSettings = { settingsPkg = it.packageName },
                    liveByPackage = sessions.filter { it.status == SessionStatus.RUNNING }
                        .associateBy { it.packageName },
                    onOpenViewer = { openViewer(it) },
                    intro = "Every launchable app can run as a hidden window of the real installed app — same account, same data. A 6dp badge means a hidden window is already live; tap the row to reopen it. For Chrome tabs, use the Browsers tab.",
                )
                Tab.Sessions -> ActiveSessionsScreen(
                    sessions = sessions,
                    onRemote = { s ->
                        context.startActivity(
                            Intent(context, RemoteControlActivity::class.java)
                                .putExtra(RemoteControlActivity.EXTRA_SESSION_ID, s.id),
                        )
                    },
                    onPreview = { s ->
                        context.startActivity(
                            Intent(context, AppViewerActivity::class.java)
                                .putExtra(AppViewerActivity.EXTRA_SESSION_ID, s.id),
                        )
                    },
                    onStop = { s ->
                        val i = Intent(context, GhostService::class.java)
                            .setAction(GhostStopReceiver.ACTION_STOP_SESSION)
                            .putExtra(GhostService.EXTRA_SESSION_ID, s.id)
                        context.startService(i)
                    },
                    onStopAll = { GhostService.stopAll(context) },
                    onMute = { s, muted ->
                        val i = Intent(context, GhostService::class.java)
                            .setAction(GhostService.ACTION_MUTE)
                            .putExtra(GhostService.EXTRA_SESSION_ID, s.id)
                            .putExtra(GhostService.EXTRA_MUTED, muted)
                        context.startService(i)
                    },
                )
                Tab.Setup -> SetupScreen()
            }
        }
    }

    pendingExisting?.let { (app, session) ->
        AlertDialog(
            onDismissRequest = { pendingExisting = null },
            title = { Text("Hidden window already running") },
            text = {
                Text(
                    "${app.label} already has a hidden window (${session.tier}). Resume it, start an additional window of the same real app, or cancel. GhostDisplay will not silently start another hidden window.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExisting = null
                    openViewer(session)
                }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val pair = pendingExisting
                    pendingExisting = null
                    if (pair != null) {
                        scope.launch {
                            val p = vm.profileOf(pair.first.packageName)
                            requestLaunch(pair.first, p, additional = true)
                        }
                    }
                }) { Text("Start additional window") }
                TextButton(onClick = { pendingExisting = null }) { Text("Cancel") }
            },
        )
    }
    pendingFinance?.let { (app, profile) ->
        AlertDialog(
            onDismissRequest = { pendingFinance = null },
            title = { Text("Finance app warning") },
            text = {
                Text(
                    "A hidden display is not isolation. ${app.label} can still use the network, notifications, and its own account. Continue only if you understand this is the same real app.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingFinance = null
                    startNow(app, profile, additional = false, financeConfirmed = true)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { pendingFinance = null }) { Text("Cancel") }
            },
        )
    }
    if (showExplainer) {
        val pair = explainerApp
        AlertDialog(
            onDismissRequest = { showExplainer = false },
            title = { Text("Hidden window") },
            text = {
                Text(
                    "This is your real ${pair?.first?.label ?: "app"}, same account, running on a hidden display. Reopen it here to pick up exactly where you left off. Your home-screen icon still opens a second window of the same real app on your main screen. GhostDisplay never fights that icon.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExplainer = false
                    explainerDone = true
                    scope.launch { ghostApp.container.prefs.setSessionExplainerSeen(true) }
                    if (pair != null) requestLaunch(pair.first, pair.second)
                }) { Text("Got it") }
            },
        )
    }
}
