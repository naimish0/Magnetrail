package com.rameshta.magnetrail.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class MagnetrailSpacing(
    val xxs: androidx.compose.ui.unit.Dp = 4.dp,
    val xs: androidx.compose.ui.unit.Dp = 8.dp,
    val sm: androidx.compose.ui.unit.Dp = 12.dp,
    val md: androidx.compose.ui.unit.Dp = 16.dp,
    val lg: androidx.compose.ui.unit.Dp = 24.dp,
    val xl: androidx.compose.ui.unit.Dp = 32.dp,
    val xxl: androidx.compose.ui.unit.Dp = 48.dp,
    val screenHorizontal: androidx.compose.ui.unit.Dp = 20.dp,
    val screenTop: androidx.compose.ui.unit.Dp = 16.dp,
    val screenBottom: androidx.compose.ui.unit.Dp = 24.dp,
)

@Immutable
data class MagnetrailDimensions(
    val minimumTouchTarget: androidx.compose.ui.unit.Dp = 48.dp,
    val primaryButtonHeight: androidx.compose.ui.unit.Dp = 56.dp,
    val secondaryButtonHeight: androidx.compose.ui.unit.Dp = 52.dp,
    val iconButtonSize: androidx.compose.ui.unit.Dp = 48.dp,
    val boardMaxSize: androidx.compose.ui.unit.Dp = 380.dp,
    val boardElevation: androidx.compose.ui.unit.Dp = 10.dp,
)

val MagnetrailShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

val LocalMagnetrailSpacing = staticCompositionLocalOf { MagnetrailSpacing() }
val LocalMagnetrailDimensions = staticCompositionLocalOf { MagnetrailDimensions() }
