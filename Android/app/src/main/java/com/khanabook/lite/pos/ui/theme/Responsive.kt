package com.khanabook.lite.pos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
enum class WindowWidthTier {
    Compact,
    Medium,
    Expanded
}

@Immutable
data class ResponsiveLayout(
    val screenWidthDp: Int,
    val widthTier: WindowWidthTier,
    val screenHeightDp: Int = 640
) {
    val isCompact: Boolean = widthTier == WindowWidthTier.Compact
    val isMedium: Boolean = widthTier == WindowWidthTier.Medium
    val isExpanded: Boolean = widthTier == WindowWidthTier.Expanded
    val isCompactForm: Boolean = screenWidthDp < 400
    val isCompactHeight: Boolean = screenHeightDp < 480
    val isLandscape: Boolean = screenWidthDp > screenHeightDp
    val reportDetailItemListMaxHeight: Dp = if (isCompactHeight) 120.dp else 200.dp

    // Layout decisions — single source of truth for all screens
    val isWideListDetail: Boolean = screenWidthDp >= 840

    // Grid layout: capped at 3 columns max for POS tap-target usability.
    // Screens use GridCells.Fixed(menuGridColumns).
    val menuGridColumns: Int = when {
        screenWidthDp >= 840 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }

    // Bottom navigation is used on all sizes (intentional product decision for v1).
    // NavigationRail may be evaluated for v2 on expanded widths.
    val useBottomNavigation: Boolean = true

    val contentPadding: Dp = when (widthTier) {
        WindowWidthTier.Compact -> 16.dp
        WindowWidthTier.Medium -> 20.dp
        WindowWidthTier.Expanded -> 24.dp
    }
    val dialogWidthFraction: Float = when (widthTier) {
        WindowWidthTier.Compact -> 0.92f
        WindowWidthTier.Medium -> 0.74f
        WindowWidthTier.Expanded -> 0.56f
    }
    val dialogMaxWidth: Dp = when (widthTier) {
        WindowWidthTier.Compact -> 420.dp
        WindowWidthTier.Medium -> 480.dp
        WindowWidthTier.Expanded -> 560.dp
    }
}

internal fun responsiveLayoutForWindowSizeClass(
    screenWidthDp: Int,
    screenHeightDp: Int,
    widthSizeClass: WindowWidthSizeClass
): ResponsiveLayout {
    val tier = when (widthSizeClass) {
        WindowWidthSizeClass.Expanded -> WindowWidthTier.Expanded
        WindowWidthSizeClass.Medium -> WindowWidthTier.Medium
        else -> WindowWidthTier.Compact
    }
    return ResponsiveLayout(
        screenWidthDp = screenWidthDp,
        widthTier = tier,
        screenHeightDp = screenHeightDp
    )
}

val LocalResponsiveLayout = staticCompositionLocalOf {
    ResponsiveLayout(screenWidthDp = 360, widthTier = WindowWidthTier.Compact, screenHeightDp = 640)
}
