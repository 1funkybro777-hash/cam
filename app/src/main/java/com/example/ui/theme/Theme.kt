package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OpticalDarkColorScheme = darkColorScheme(
    primary = SignalCyan,
    onPrimary = Color(0xFF04131D),
    primaryContainer = SignalCyanDim,
    onPrimaryContainer = SignalCyan,
    secondary = LaserAmber,
    onSecondary = Color(0xFF1E1702),
    secondaryContainer = LaserAmberDim,
    onSecondaryContainer = LaserAmber,
    tertiary = EmeraldLock,
    onTertiary = Color(0xFF041A0B),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = HudSurfaceSolid,
    onSurface = TextPrimary,
    surfaceVariant = HudSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = HudBorder,
    error = CoralAlert,
    onError = Color(0xFF26030A),
)

@Composable
fun FrameLockTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = OpticalDarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
