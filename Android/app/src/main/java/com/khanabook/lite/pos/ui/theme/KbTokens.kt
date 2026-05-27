package com.khanabook.lite.pos.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════
// KHANBOOK DLS — SHAPE + BUTTON + SPACING TOKENS
//
// All sizing tokens for consistent layout and component sizing.
// ═══════════════════════════════════════════════════════════════

// ── Button Heights ────────────────────────────────────────────
object KbButtonSize {
    /** Primary CTA — "Pay Now", "Place Order", "Submit" */
    val HeightLarge: Dp = 48.dp

    /** Secondary action — "Cancel", "Edit", "Save Draft" */
    val HeightMedium: Dp = 44.dp

    /** Inline / compact — table row actions, chip buttons */
    val HeightSmall: Dp = 32.dp

    /** Icon-only button diameter */
    val HeightIcon: Dp = 40.dp

    // Padding inside buttons
    val PaddingLarge  = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    val PaddingMedium = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val PaddingSmall  = PaddingValues(horizontal = 14.dp, vertical = 8.dp)

    // Minimum width for text buttons
    val MinWidthLarge: Dp  = 160.dp
    val MinWidthMedium: Dp = 120.dp
    val MinWidthSmall: Dp  = 80.dp
}

// ── Corner Radii (Shape Tokens) ───────────────────────────────
object KbShape {
    /** Small components — badges, extra-small elements */
    val ExtraSmall = RoundedCornerShape(6.dp)

    /** Inputs, search fields */
    val Small = RoundedCornerShape(10.dp)

    /** Standard buttons, small cards */
    val Medium = RoundedCornerShape(14.dp)

    /** Large cards, modals, bottom sheets */
    val Large = RoundedCornerShape(18.dp)

    /** Fully rounded — FAB, avatar, pill chips */
    val ExtraLarge = RoundedCornerShape(50)

    /** Bottom sheet top corners only */
    val BottomSheet = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    /** Dialog */
    val Dialog = RoundedCornerShape(18.dp)
}

// ── Spacing / Padding Tokens ─────────────────────────────────
object KbSpacing {
    val XS:  Dp = 4.dp
    val S:   Dp = 8.dp
    val M:   Dp = 12.dp
    val L:   Dp = 16.dp
    val XL:  Dp = 20.dp
    val XXL: Dp = 24.dp
    val XXXL: Dp = 32.dp

    /** Standard screen horizontal padding */
    val ScreenHorizontal: Dp = 16.dp

    /** Standard card internal padding */
    val CardPadding: Dp = 16.dp

    /** Section gap between cards */
    val SectionGap: Dp = 12.dp

    /** List item vertical padding */
    val ListItemVertical: Dp = 12.dp
}

// ── Icon Sizes ────────────────────────────────────────────────
object KbIconSize {
    val XS: Dp = 14.dp   // inline badge
    val S:  Dp = 18.dp   // nav bar secondary
    val M:  Dp = 22.dp   // nav bar primary, list row icon
    val L:  Dp = 28.dp   // feature icon
    val XL: Dp = 40.dp   // empty state, onboarding
    val XXL: Dp = 64.dp  // splash logo
}

// ── Elevation ─────────────────────────────────────────────────
object KbElevation {
    val None:   Dp = 0.dp
    val Low:    Dp = 1.dp    // subtle card lift
    val Medium: Dp = 3.dp    // active card, FAB
    val High:   Dp = 6.dp    // bottom sheet, dialog shadow
    val Modal:  Dp = 12.dp   // full-screen modal
}
