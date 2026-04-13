package com.khanabook.lite.pos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
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
    val widthTier: WindowWidthTier
) {
    val isCompact: Boolean = widthTier == WindowWidthTier.Compact
    val isMedium: Boolean = widthTier == WindowWidthTier.Medium
    val isExpanded: Boolean = widthTier == WindowWidthTier.Expanded
    val isCompactForm: Boolean = screenWidthDp < 400
    val isWideListDetail: Boolean = screenWidthDp >= 840
    val menuGridColumns: Int = when {
        screenWidthDp >= 1200 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }
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

internal fun responsiveLayoutForWidth(screenWidthDp: Int): ResponsiveLayout {
    val tier = when {
        screenWidthDp >= 840 -> WindowWidthTier.Expanded
        screenWidthDp >= 600 -> WindowWidthTier.Medium
        else -> WindowWidthTier.Compact
    }
    return ResponsiveLayout(
        screenWidthDp = screenWidthDp,
        widthTier = tier
    )
}

val LocalResponsiveLayout = staticCompositionLocalOf {
    responsiveLayoutForWidth(screenWidthDp = 360)
}
