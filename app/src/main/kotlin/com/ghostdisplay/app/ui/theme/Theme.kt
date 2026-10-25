package com.ghostdisplay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GhostMint = Color(0xFF7AE7C7)
val GhostMintDim = Color(0xFF3D8F7C)
val GhostBg = Color(0xFF0B0D10)
val GhostSurface = Color(0xFF141820)
val GhostSurface2 = Color(0xFF1C2230)
val GhostOutline = Color(0xFF2A3344)
val GhostText = Color(0xFFE8ECF1)
val GhostMuted = Color(0xFF9AA3B2)
val GhostDanger = Color(0xFFFF6B6B)
val GhostWarn = Color(0xFFFFC857)
val GhostOk = Color(0xFF7AE7C7)

private val colors = darkColorScheme(
    primary = GhostMint,
    onPrimary = Color(0xFF04221A),
    primaryContainer = Color(0xFF1A3D34),
    onPrimaryContainer = GhostMint,
    secondary = Color(0xFF8B9CFF),
    onSecondary = Color(0xFF101225),
    background = GhostBg,
    onBackground = GhostText,
    surface = GhostSurface,
    onSurface = GhostText,
    surfaceVariant = GhostSurface2,
    onSurfaceVariant = GhostMuted,
    outline = GhostOutline,
    error = GhostDanger,
    onError = Color(0xFF3B0A0A),
)

private val typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = GhostMuted),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun GhostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content,
    )
}
