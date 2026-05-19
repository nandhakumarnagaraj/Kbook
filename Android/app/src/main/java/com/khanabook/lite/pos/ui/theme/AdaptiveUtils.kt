package com.khanabook.lite.pos.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowWidthSizeClass { Compact, Medium, Expanded }

data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowWidthSizeClass
)

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val config = LocalConfiguration.current
    val widthClass = when {
        config.screenWidthDp >= 840 -> WindowWidthSizeClass.Expanded
        config.screenWidthDp >= 600 -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Compact
    }
    val heightClass = when {
        config.screenHeightDp >= 900 -> WindowWidthSizeClass.Expanded
        config.screenHeightDp >= 480 -> WindowWidthSizeClass.Medium
        else -> WindowWidthSizeClass.Compact
    }
    return WindowSizeClass(widthClass, heightClass)
}

@Composable
fun isTablet(): Boolean {
    val wsc = rememberWindowSizeClass()
    return wsc.widthSizeClass == WindowWidthSizeClass.Expanded
}
