package com.ghostdisplay.app.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ghostdisplay.app.GhostApp
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.launch.HiddenLaunch
import com.ghostdisplay.app.profile.AppCategory
import com.ghostdisplay.app.profile.AppCategoryClassifier
import com.ghostdisplay.app.service.MediaProjectionHolder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class PendingStart(
    val app: LaunchableApp,
    val profile: AppProfile?,
    val additional: Boolean,
    val financeConfirmed: Boolean,
)

class LaunchController(
    val requestLaunch: (LaunchableApp, AppProfile?) -> Unit,
    val openViewer: (GhostSession) -> Unit,
    val stop: (GhostSession) -> Unit,
    val stopAll: () -> Unit,
    val openRemote: (GhostSession) -> Unit,
)

@Composable
fun LaunchGate(
    vm: GhostViewModel,
    onLaunched: () -> Unit = {},
    content: @Composable (LaunchController) -> Unit,
) {
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ghostApp = context.applicationContext as? GhostApp
    val financeFlow = remember(ghostApp) { ghostApp?.container?.prefs?.financeWarn ?: flowOf(true) }
    val explainerFlow = remember(ghostApp) { ghostApp?.container?.prefs?.sessionExplainerSeen ?: flowOf(false) }
    val financeOn by financeFlow.collectAsState(initial = true)
    val explainerSeen by explainerFlow.collectAsState(initial = false)

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
        val hardNeed = HiddenLaunch.hardNeedsMediaProjection(pending.profile)
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            HiddenLaunch.start(
                context,
                pending.app,
                result.resultCode,
                result.data,
                additionalWindow = pending.additional,
                financeConfirmed = pending.financeConfirmed,
            )
            onLaunched()
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
            HiddenLaunch.start(
                context,
                pending.app,
                additionalWindow = pending.additional,
                financeConfirmed = pending.financeConfirmed,
            )
            onLaunched()
        }
    }

    fun startNow(app: LaunchableApp, profile: AppProfile?, additional: Boolean, financeConfirmed: Boolean) {
        if (HiddenLaunch.needsMediaProjection(profile) && !MediaProjectionHolder.hasConsent()) {
            pendingLaunch = PendingStart(app, profile, additional, financeConfirmed)
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                mpLauncher.launch(mgr.createScreenCaptureIntent())
            } catch (t: Throwable) {
                pendingLaunch = null
                Toast.makeText(
                    context,
                    "Could not open screen-capture consent: ${t.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        } else {
            HiddenLaunch.start(
                context,
                app,
                additionalWindow = additional,
                financeConfirmed = financeConfirmed,
            )
            onLaunched()
        }
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

    val controller = LaunchController(
        requestLaunch = { app, profile -> requestLaunch(app, profile) },
        openViewer = { HiddenLaunch.openViewer(context, it) },
        stop = { HiddenLaunch.stop(context, it) },
        stopAll = { HiddenLaunch.stopAll(context) },
        openRemote = { HiddenLaunch.openRemote(context, it) },
    )

    content(controller)

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
                    HiddenLaunch.openViewer(context, session)
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
                    scope.launch { ghostApp?.container?.prefs?.setSessionExplainerSeen(true) }
                    if (pair != null) requestLaunch(pair.first, pair.second)
                }) { Text("Got it") }
            },
        )
    }
}
