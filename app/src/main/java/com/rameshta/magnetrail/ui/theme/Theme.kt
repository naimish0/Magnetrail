package com.rameshta.magnetrail.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MagnetrailLightColors = lightColorScheme(
    primary = MagnetrailPrimary,
    onPrimary = MagnetrailSurface,
    primaryContainer = MagnetrailPullSoft,
    onPrimaryContainer = MagnetrailPrimaryStrong,
    secondary = MagnetrailPull,
    onSecondary = MagnetrailSurface,
    tertiary = MagnetrailPush,
    onTertiary = MagnetrailPrimaryStrong,
    background = MagnetrailBackground,
    onBackground = MagnetrailInk,
    surface = MagnetrailSurface,
    onSurface = MagnetrailInk,
    surfaceVariant = MagnetrailPullSoft,
    onSurfaceVariant = MagnetrailMuted,
    outline = MagnetrailBorder,
    error = MagnetrailError,
    onError = MagnetrailSurface,
)

@Composable
fun MagnetrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MagnetrailLightColors,
        typography = Typography,
        content = content,
    )
}
