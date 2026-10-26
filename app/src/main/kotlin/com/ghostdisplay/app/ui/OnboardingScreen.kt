package com.ghostdisplay.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostSurface2
import kotlinx.coroutines.launch

private data class Page(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = listOf(
        Page(
            Icons.Outlined.TouchApp,
            "Hold an app. That’s it.",
            "Open GhostDisplay once. Finish Setup (Shizuku + Accessibility). After that, leave it. On your home screen, hold Chrome, WhatsApp, or any app — Run hidden appears. Hold it again to stop. You do not come back here to start or stop a hidden window.",
        ),
        Page(
            Icons.Outlined.WarningAmber,
            "Shizuku is required here",
            "HyperOS will not let GhostDisplay start another app on a hidden display by itself. Shizuku gives GhostDisplay a shell identity that Android does allow. Pair Shizuku once (you may already have), tap Start, then Grant Shizuku in Setup. No root. After a reboot, only Start is needed again.",
        ),
        Page(
            Icons.Outlined.Language,
            "Your real Chrome, hidden",
            "GhostDisplay launches the Chrome (or Firefox) already on your phone — the real app, same tabs, same account — on a hidden virtual display. Open at least 10 tabs in the Viewer. The display stays live with a wake lock, so the browser is not backgrounded the way it is when you leave it for the home screen. Closing the Viewer does not stop it.",
        ),
        Page(
            Icons.Outlined.VisibilityOff,
            "Nothing shows on your screen",
            "The hidden window is not a floating bubble, not split-screen, not picture-in-picture, not a mirror of this screen. The app keeps rendering and playing audio as if it were in front of you. You use something else on the real screen.",
        ),
        Page(
            Icons.Outlined.WarningAmber,
            "Hidden is not isolated",
            "A virtual display hides the picture, not the system. The hidden app can still use the network, send notifications, and change settings. Finance apps warn before start. Enable Accessibility on the next screens so hold-on-home works; without it, hold the app in this list instead — GhostDisplay will say so, not fail silently.",
        ),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("GhostDisplay", style = MaterialTheme.typography.headlineLarge, color = GhostMint)
        Text("Hold an app to run it on a screen that isn’t there.", color = GhostMuted)
        Spacer(Modifier.height(32.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val p = pages[index]
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GhostSurface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(p.icon, contentDescription = null, tint = GhostMint, modifier = Modifier.size(36.dp))
                }
                Text(p.title, style = MaterialTheme.typography.headlineMedium)
                Text(p.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(if (pager.currentPage == i) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) GhostMint else GhostSurface2),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (pager.currentPage < pages.lastIndex) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                } else onFinished()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (pager.currentPage == pages.lastIndex) "Continue" else "Next")
        }
        TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
            Text("Skip")
        }
    }
}
