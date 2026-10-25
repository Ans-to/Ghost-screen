package com.ghostdisplay.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ghostdisplay.app.data.LaunchableApp
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
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val activityName = intent.getStringExtra(EXTRA_ACTIVITY).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { null } ?: pkg.orEmpty()
        if (pkg.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            GhostTheme {
                val rows by vm.rows.collectAsState()
                val sessions by vm.sessions.collectAsState()
                val catalogRow = remember(rows, pkg) { rows.firstOrNull { it.app.packageName == pkg } }
                val app = catalogRow?.app ?: LaunchableApp(
                    packageName = pkg,
                    activityName = activityName,
                    label = label,
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    resizeable = true,
                )
                val live = sessions.firstOrNull {
                    it.packageName == pkg && it.status == SessionStatus.RUNNING
                }
                val scope = rememberCoroutineScope()
                LaunchedEffect(pkg) { vm.refreshApps() }
                LaunchGate(vm, onLaunched = {
                    Toast.makeText(
                        this@HoldActionActivity,
                        "${app.label} is running hidden",
                        Toast.LENGTH_SHORT,
                    ).show()
                    finish()
                }) { gate ->
                    AppActionSheet(
                        target = AppActionTarget(app, catalogRow?.icon, live),
                        onDismiss = { finish() },
                        onRunHidden = { target ->
                            scope.launch {
                                val p = vm.profileOf(target.packageName)
                                gate.requestLaunch(target, p)
                            }
                        },
                        onStopHidden = { session ->
                            gate.stop(session)
                            Toast.makeText(
                                this@HoldActionActivity,
                                "${app.label} stopped",
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
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_ACTIVITY, activityName)
                putExtra(EXTRA_LABEL, label)
            }
        }
    }
}
