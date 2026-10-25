package com.ghostdisplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ghostdisplay.app.launch.ForegroundLaunchHost
import com.ghostdisplay.app.ui.GhostAppUi
import com.ghostdisplay.app.ui.GhostViewModel
import com.ghostdisplay.app.ui.theme.GhostTheme

class MainActivity : ComponentActivity() {

    private val vm: GhostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GhostTheme {
                Surface(Modifier.fillMaxSize()) {
                    GhostAppUi(vm)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ForegroundLaunchHost.onResume(this)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshApps()
    }

    override fun onStop() {
        ForegroundLaunchHost.onPause(this)
        super.onStop()
    }

    companion object {
        const val EXTRA_RESTORE = "restore"
    }
}
