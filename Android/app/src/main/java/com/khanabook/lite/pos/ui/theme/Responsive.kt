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
enum class TypeScaleTier {
    CompactPhone,
    MediumPhone,
    LargePhone,
    Tablet
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
    val isCompactHeight: Boolean = screenHeightDp < 640
    // 360x640 is the smallest mainstream window (small phones); Home must fit all
    // 5 actions without scrolling there too, so its trims kick in at <= 640.
    val compactHomeHeight: Boolean = screenHeightDp <= 640
    val isTallScreen: Boolean = screenHeightDp >= 800
    val isLandscape: Boolean = screenWidthDp > screenHeightDp
    val reportDetailItemListMaxHeight: Dp = if (isCompactHeight) 120.dp else 200.dp

    // Controlled typography tier — resolved from the CURRENT window (width + height),
    // never from device model. Used to pick one of 4 fixed Typography variants.
    val typeScaleTier: TypeScaleTier = when {
        screenWidthDp >= 600 && screenHeightDp >= 700 -> TypeScaleTier.Tablet
        screenHeightDp < 640 || screenWidthDp < 390 -> TypeScaleTier.CompactPhone
        screenWidthDp >= 420 && screenHeightDp >= 880 -> TypeScaleTier.LargePhone
        else -> TypeScaleTier.MediumPhone
    }

    // Interior padding for cards — grows moderately with available space so cards stay
    // content-driven but breathe more on larger windows (never stretched).
    val cardPaddingHorizontal: Dp = when (typeScaleTier) {
        TypeScaleTier.CompactPhone -> 14.dp
        TypeScaleTier.MediumPhone -> 16.dp
        TypeScaleTier.LargePhone -> 18.dp
        TypeScaleTier.Tablet -> 20.dp
    }
    val cardPaddingVertical: Dp = when (typeScaleTier) {
        TypeScaleTier.CompactPhone -> 10.dp
        TypeScaleTier.MediumPhone -> 12.dp
        TypeScaleTier.LargePhone -> 14.dp
        TypeScaleTier.Tablet -> 16.dp
    } + if (isTallScreen) 4.dp else 0.dp

    // Hero action (primary CTA) interior padding — taller hierarchy anchor
    val primaryCardVertical: Dp = when (typeScaleTier) {
        TypeScaleTier.CompactPhone -> 20.dp
        TypeScaleTier.MediumPhone -> 24.dp
        TypeScaleTier.LargePhone -> 28.dp
        TypeScaleTier.Tablet -> 48.dp
    } + if (isTallScreen) 8.dp else 0.dp

    // Action card icon containers — larger touch visuals as space grows
    val actionIconContainerSize: Dp = when (typeScaleTier) {
        TypeScaleTier.CompactPhone -> 40.dp
        TypeScaleTier.MediumPhone -> 44.dp
        TypeScaleTier.LargePhone -> 48.dp
        TypeScaleTier.Tablet -> 52.dp
    }
    val actionIconSize: Dp = when (typeScaleTier) {
        TypeScaleTier.CompactPhone -> 20.dp
        TypeScaleTier.MediumPhone -> 22.dp
        TypeScaleTier.LargePhone -> 24.dp
        TypeScaleTier.Tablet -> 26.dp
    }
    val primaryIconContainerSize: Dp = when (typeScaleTier) {
        TypeScaleTier.CompactPhone -> 44.dp
        TypeScaleTier.MediumPhone -> 48.dp
        TypeScaleTier.LargePhone -> 52.dp
        TypeScaleTier.Tablet -> 56.dp
    }
    val primaryIconSize: Dp = when (typeScaleTier) {
        TypeScaleTier.CompactPhone -> 24.dp
        TypeScaleTier.MediumPhone -> 24.dp
        TypeScaleTier.LargePhone -> 26.dp
        TypeScaleTier.Tablet -> 28.dp
    }

    // Single-column action cards (v1 design decision).
    // Landscape gets 2 columns so all 4 actions stay visible without scroll.
    val homeActionColumns: Int = if (isLandscape) 2 else 1

    // Section spacing rhythm — grows with available height so residual space between
    // sections is absorbed by the spacing system, not by stretching cards.
    val sectionSpacing: Dp = when {
        screenHeightDp < 640 -> 8.dp
        screenHeightDp < 800 -> 16.dp
        screenHeightDp < 1000 -> 24.dp
        screenHeightDp < 1300 -> 32.dp
        else -> 40.dp
    }

    // Upper bound for extra space distributed into a single inter-section gap.
    // Scales with available height so tall phones (20:9) absorb more residual space
    // into rhythm instead of pooling it all as dead bottom margin.
    // TODO: Determine correct values after responsive design audit.
    // Runtime measurements show:
    //   OnePlus Nord (384x797dp, CompactPhone): ~283px slack, 2 gaps
    //   Moto G34 (411x914dp, MediumPhone): ~31px slack, 2 gaps
    //   Lenovo Tablet (800x1208dp, Tablet): ~379dp slack, 2 gaps
    val maxSectionGap: Dp = 0.dp

    // Height-adaptive hero sizing — scales major visual elements with available space
    val heroImageSize: Dp = when {
        screenHeightDp < 640 -> minOf((screenHeightDp * 0.18f).toInt(), 100).dp
        screenHeightDp > 800 -> 160.dp
        else -> minOf((screenHeightDp * 0.22f).toInt(), 140).dp
    }

    val qrCodeSize: Dp = when {
        isCompact && screenHeightDp < 640 -> minOf((screenWidthDp * 0.4f).toInt(), 160).dp
        isExpanded -> minOf((screenWidthDp * 0.35f).toInt(), 220).dp
        else -> minOf((screenWidthDp * 0.5f).toInt(), 200).dp
    }

    val logoSize: Dp = when {
        screenHeightDp < 640 -> 80.dp
        screenHeightDp > 800 -> 120.dp
        else -> 100.dp
    }

    // Content width cap — prevents over-stretching on tablets/foldables/ChromeOS
    val maxContentWidth: Dp = when (widthTier) {
        WindowWidthTier.Compact -> Dp.Unspecified
        WindowWidthTier.Medium -> 560.dp
        WindowWidthTier.Expanded -> 720.dp
    }

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
        WindowWidthTier.Medium -> 0.82f
        WindowWidthTier.Expanded -> 0.68f
    }
    val dialogMaxWidth: Dp = when (widthTier) {
        WindowWidthTier.Compact -> 420.dp
        WindowWidthTier.Medium -> 600.dp
        WindowWidthTier.Expanded -> 680.dp
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

val LocalTypeScale = staticCompositionLocalOf { TypeScaleTier.MediumPhone }
