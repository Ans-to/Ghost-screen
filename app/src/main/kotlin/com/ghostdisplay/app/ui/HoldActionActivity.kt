package com.ghostdisplay.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ghostdisplay.app.MainActivity
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.ui.theme.GhostTheme
import kotlinx.coroutines.launch

/**
 * Translucent activity shown after a home-screen long-press.
 * Not a floating window and not a draw-over-apps overlay — a real activity
 * the user dismisses like a chooser.
 */
class HoldActionActivity : ComponentActivity() {

    private val vm: GhostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        setContent {
            GhostTheme {
                val onboarding by vm.onboardingComplete.collectAsState()
                val warning by vm.warningAccepted.collectAsState()
                if (!onboarding || !warning) {
                    LaunchedEffect(Unit) {
                        startActivity(Intent(this@HoldActionActivity, MainActivity::class.java))
                        finish()
                    }
                    return@setContent
                }
                if (pkg.isNullOrBlank()) {
                    LaunchedEffect(Unit) { finish() }
                    return@setContent
                }
                val rows by vm.rows.collectAsState()
                val sessions by vm.sessions.collectAsState()
                val row = remember(rows, pkg) { rows.firstOrNull { it.app.packageName == pkg } }
                val scope = rememberCoroutineScope()
                LaunchedEffect(pkg) { vm.refreshApps() }
                if (row == null && rows.isNotEmpty()) {
                    LaunchedEffect(pkg) {
                        Toast.makeText(
                            this@HoldActionActivity,
                            "Could not match that app. Open GhostDisplay and hold it in the list.",
                            Toast.LENGTH_LONG,
                        ).show()
                        finish()
                    }
                    return@setContent
                }
                if (row == null) return@setContent
                val live = sessions.firstOrNull {
                    it.packageName == pkg && it.status == SessionStatus.RUNNING
                }
                LaunchGate(vm, onLaunched = {
                    Toast.makeText(
                        this@HoldActionActivity,
                        "${row.app.label} is running hidden",
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }) { gate ->
                    AppActionSheet(
                        target = AppActionTarget(row.app, row.icon, live),
                        onDismiss = { finish() },
                        onRunHidden = { app ->
                            scope.launch {
                                val p = vm.profileOf(app.packageName)
                                gate.requestLaunch(app, p)
                            }
                        },
                        onStopHidden = { session ->
                            gate.stop(session)
                            Toast.makeText(
                                this@HoldActionActivity,
                                "${row.app.label} stopped",
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        },
                        onOpenViewer = { session ->
                            gate.openViewer(session)
                            finish()
                        },
                        onSettings = null,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_ACTIVITY = "activity"
        const val EXTRA_LABEL = "label"

        fun intent(context: Context, packageName: String, activityName: String, label: String): Intent {
            return Intent(context, HoldActionActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_ACTIVITY, activityName)
                putExtra(EXTRA_LABEL, label)
            }
        }
    }
}
