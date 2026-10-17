package com.khanabook.lite.pos.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val default: Dp = 0.dp,
    val hairline: Dp = 2.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val smallMedium: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val mediumLarge: Dp = 20.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
    val extraHuge: Dp = 64.dp,
    val screenContentPadding: Dp = 16.dp,
    val bottomListPadding: Dp = 88.dp,

    // Standard control heights (all >= 48dp minimum touch target).
    // These defaults are the MediumPhone tier; spacingForTier() overrides them
    // per window size, so read them through KhanaBookTheme.spacing, never directly.
    val buttonHeightCompact: Dp = 48.dp,
    val buttonHeight: Dp = 52.dp,
    val buttonHeightLarge: Dp = 56.dp,
    val inputHeight: Dp = 56.dp
)

/**
 * Gap scale per type tier.
 *
 * The single multiplier is deliberately kept in step with the typography tier
 * (Type.kt TypeTierScales) so dp gaps stay visually proportional to the sp text
 * they sit between: when text grows on a tablet the gaps grow with it, and on a
 * compact phone text shrinks to 0.90x so the gaps shrink to match. Without this
 * the rhythm breaks — tight text in roomy gaps on small screens, and cramped
 * text in tight gaps on large ones.
 *
 * MediumPhone is exactly 1.00, so the most common handset renders identically to
 * the previous fixed-Spacing behaviour.
 */
private val GapScaleByTier: Map<TypeScaleTier, Float> = mapOf(
    TypeScaleTier.CompactPhone to 0.90f,
    TypeScaleTier.MediumPhone to 1.00f,
    TypeScaleTier.LargePhone to 1.05f,
    TypeScaleTier.Tablet to 1.15f
)

/**
 * Control heights per type tier.
 *
 * Not a plain multiplier on the gap scale: 48dp is the Material minimum touch
 * target, so CompactPhone holds at the floor instead of scaling down to 43dp.
 * Shrinking a POS tap target to buy visual breathing room is the wrong trade.
 */
private data class ControlHeights(
    val compact: Dp,
    val standard: Dp,
    val large: Dp,
    val input: Dp
)

private val ControlHeightsByTier: Map<TypeScaleTier, ControlHeights> = mapOf(
    TypeScaleTier.CompactPhone to ControlHeights(
        compact = 48.dp, standard = 48.dp, large = 52.dp, input = 52.dp
    ),
    TypeScaleTier.MediumPhone to ControlHeights(
        compact = 48.dp, standard = 52.dp, large = 56.dp, input = 56.dp
    ),
    TypeScaleTier.LargePhone to ControlHeights(
        compact = 52.dp, standard = 56.dp, large = 60.dp, input = 60.dp
    ),
    TypeScaleTier.Tablet to ControlHeights(
        compact = 58.dp, standard = 62.dp, large = 68.dp, input = 68.dp
    )
)

/**
 * Builds the Spacing token set for the current window.
 *
 * Every screen already reads gaps and control heights through
 * `KhanaBookTheme.spacing.*`, so resolving this once in the theme provider makes
 * spacing responsive app-wide without touching individual screens.
 */
internal fun spacingForTier(tier: TypeScaleTier): Spacing {
    val scale = GapScaleByTier[tier] ?: 1.0f
    val control = ControlHeightsByTier[tier] ?: ControlHeightsByTier.getValue(TypeScaleTier.MediumPhone)
    return Spacing(
        default = 0.dp,
        hairline = 2.dp * scale,
        extraSmall = 4.dp * scale,
        small = 8.dp * scale,
        smallMedium = 12.dp * scale,
        medium = 16.dp * scale,
        mediumLarge = 20.dp * scale,
        large = 24.dp * scale,
        extraLarge = 32.dp * scale,
        huge = 48.dp * scale,
        extraHuge = 64.dp * scale,
        screenContentPadding = 16.dp * scale,
        bottomListPadding = 88.dp * scale,
        buttonHeightCompact = control.compact,
        buttonHeight = control.standard,
        buttonHeightLarge = control.large,
        inputHeight = control.input
    )
}

val LocalSpacing = staticCompositionLocalOf { Spacing() }
