package com.khanabook.lite.pos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class IconSize(
    val xsmall: Dp = 14.dp,
    val small: Dp = 18.dp,
    val medium: Dp = 24.dp,
    val large: Dp = 32.dp,
    val xlarge: Dp = 48.dp,
    val xxlarge: Dp = 64.dp,
    val avatar: Dp = 52.dp,
    val heroCircle: Dp = 80.dp,
    val hero: Dp = 100.dp
)

val LocalIconSize = staticCompositionLocalOf { IconSize() }
