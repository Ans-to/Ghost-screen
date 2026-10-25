package com.ghostdisplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ghostdisplay.app.service.GhostService
import com.ghostdisplay.app.ui.GhostAppUi
import com.ghostdisplay.app.ui.GhostViewModel
import com.ghostdisplay.app.ui.theme.GhostTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: GhostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CoroutineScope(Dispatchers.IO).launch {
            val tabs = (application as GhostApp).container.tabs.openTabs()
            if (tabs.isNotEmpty()) {
                GhostService.startBrowser(this@MainActivity)
            }
        }
        setContent {
            GhostTheme {
                Surface(Modifier.fillMaxSize()) {
                    GhostAppUi(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshApps()
    }

    companion object {
        const val EXTRA_RESTORE = "restore"
    }
}
