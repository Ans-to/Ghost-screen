package com.ghostdisplay.app.ui

import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.ghostdisplay.app.ui.theme.GhostDanger
import com.ghostdisplay.app.ui.theme.GhostMint
import com.ghostdisplay.app.ui.theme.GhostMuted
import com.ghostdisplay.app.ui.theme.GhostSurface
import com.ghostdisplay.app.ui.theme.GhostSurface2
import kotlin.math.abs

data class SwitcherCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val pinned: Boolean = false,
    val live: Boolean = false,
    val audio: Boolean = false,
    val icon: Drawable? = null,
)

fun accentFor(key: String): Color {
    val palette = listOf(
        Color(0xFF1B3A4B),
        Color(0xFF1E3D32),
        Color(0xFF3B2A1A),
        Color(0xFF2A2440),
        Color(0xFF3A1F2A),
        Color(0xFF1A3340),
        Color(0xFF2C3E2A),
        Color(0xFF3D2E1A),
    )
    return palette[abs(key.hashCode()) % palette.size]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabCard(
    card: SwitcherCard,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val bmp = remember(card.id) { card.icon?.toBitmap(192, 192) }
    val shape = RoundedCornerShape(20.dp)
    val borderColor = when {
        selected -> GhostMint
        card.live -> GhostMint.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GhostSurface2)
            .border(2.dp, borderColor, shape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onLongPress()
                },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting || selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) GhostMint else GhostMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            if (card.pinned) {
                Icon(Icons.Outlined.PushPin, contentDescription = "Pinned", tint = GhostMint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                card.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .background(accentFor(card.id)),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GhostSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        card.title.take(1).uppercase(),
                        color = GhostMint,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            if (card.live) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(10.dp)
                        .background(GhostMint, CircleShape),
                )
            }
            if (card.audio) {
                Text("♪", color = GhostMint, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
        }
        Text(
            card.subtitle,
            color = GhostMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun TabSelectBar(
    count: Int,
    onDone: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onCloseSelected: (() -> Unit)? = null,
    closeLabel: String = "Close",
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GhostSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (count == 1) "1 selected" else "$count selected",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        if (onOpen != null && count == 1) {
            FilledTonalButton(onClick = onOpen) { Text("Open") }
        }
        if (onCloseSelected != null && count > 0) {
            Button(
                onClick = onCloseSelected,
                colors = ButtonDefaults.buttonColors(containerColor = GhostDanger),
            ) { Text(closeLabel) }
        }
        TextButton(onClick = onDone) { Text("Done") }
    }
}
