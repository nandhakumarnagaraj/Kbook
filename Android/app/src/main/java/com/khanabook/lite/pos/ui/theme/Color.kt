package com.khanabook.lite.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// KHANBOOK DLS — SAFFRON & AMBER BRAND PALETTE
// 11-shade scale, warm orange-gold culinary tone
// ═══════════════════════════════════════════════════════════════
val KbSaffron50  = Color(0xFFFFFBFF)  // on-primary-container
val KbSaffron100 = Color(0xFFFFDBCA)  // primary-fixed
val KbSaffron200 = Color(0xFFFFB68E)  // primary-fixed-dim
val KbSaffron300 = Color(0xFFE07B3A)
val KbSaffron400 = Color(0xFFBF5500)  // primary-container
val KbSaffron500 = Color(0xFFA84C00)
val KbSaffron600 = Color(0xFF984300)  // BRAND SAFFRON (Stitch primary)
val KbSaffron700 = Color(0xFF773300)  // on-primary-fixed-variant
val KbSaffron800 = Color(0xFF5C2500)
val KbSaffron900 = Color(0xFF4A1D00)
val KbSaffron950 = Color(0xFF331200)  // on-primary-fixed

// DESIGN.md brand colors
val KbBrandSaffron = Color(0xFFC85A00)
val KbBrandSaffronLight = Color(0xFFE8832A)
val KbBrandSaffronDark = Color(0xFF994500)
val KbBrandSaffronAndroid = Color(0xFFD97706)

// Pure neutral gray scale
val KbGray50  = Color(0xFFF8F9FA)
val KbGray100 = Color(0xFFF0F0F0)
val KbGray200 = Color(0xFFE0E0E0)
val KbGray300 = Color(0xFFBDBDBD)
val KbGray400 = Color(0xFF9E9E9E)
val KbGray500 = Color(0xFF757575)
val KbGray600 = Color(0xFF616161)
val KbGray700 = Color(0xFF424242)
val KbGray800 = Color(0xFF303030)
val KbGray900 = Color(0xFF212121)
val KbGray950 = Color(0xFF0D0D0D)

// Secondary — warm sage green (food-complementary to orange)
val KbGreenSec100 = Color(0xFFE3F2E9)  // light container
val KbGreenSec200 = Color(0xFFC8E6D4)
val KbGreenSec300 = Color(0xFFA5D6B7)
val KbGreenSec400 = Color(0xFF78B892)
val KbGreenSec500 = Color(0xFF4B9A6E)
val KbGreenSec600 = Color(0xFF3D7A5A)  // brand secondary (light)
val KbGreenSec700 = Color(0xFF2E5C44)
val KbGreenSec800 = Color(0xFF1E4D32)  // container (dark)
val KbGreenSec900 = Color(0xFF143D26)
val KbGreenSec950 = Color(0xFF0A2E1A)

// Semantic status
val KbGreen   = Color(0xFF16A34A)
val KbGreenSubtle  = Color(0xFFF0FDF4)
val KbGreenDark    = Color(0xFF052E16)
val KbRed     = Color(0xFFBA1A1A)
val KbRedSubtle    = Color(0xFFFFDAD6)
val KbRedDark      = Color(0xFF93000A)
val KbYellow  = Color(0xFFD97706)
val KbYellowSubtle = Color(0xFFFFFBEB)
val KbYellowDark   = Color(0xFF451A03)
val KbBlue    = Color(0xFF005BAF)
val KbBlueSubtle   = Color(0xFFF0F9FF)
val KbBlueDark     = Color(0xFF001B3C)

// Payment brand colours
val KbZomatoRed   = Color(0xFFEF4444)
val KbSwiggyOrange = Color(0xFFF97316)
val KbWhatsAppGreen = Color(0xFF22C55E)

// ═══════════════════════════════════════════════════════════════
// GLOBAL DARK MODE FLAG — used by legacy color aliases below.
// The composable ThemeState system is now in ThemeState.kt.
// ═══════════════════════════════════════════════════════════════
var globalIsDark: Boolean by mutableStateOf(true)

// ═══════════════════════════════════════════════════════════════
// MATERIAL THEME EXTENSION PROPERTIES
// Theme-aware — reads from current colorScheme
// ═══════════════════════════════════════════════════════════════

val MaterialTheme.kbBgPrimary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.background

val MaterialTheme.kbBgSecondary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.surfaceVariant

val MaterialTheme.kbBgCard: Color
    @Composable @ReadOnlyComposable get() = colorScheme.surface

val MaterialTheme.kbPrimary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.primary

val MaterialTheme.kbPrimaryBold: Color
    @Composable @ReadOnlyComposable get() = colorScheme.primaryContainer

val MaterialTheme.kbPrimarySubtle: Color
    @Composable @ReadOnlyComposable get() = colorScheme.primary.copy(alpha = 0.12f)

val MaterialTheme.kbSecondary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.secondary

val MaterialTheme.kbSecondaryBold: Color
    @Composable @ReadOnlyComposable get() = colorScheme.secondaryContainer

val MaterialTheme.kbSecondarySubtle: Color
    @Composable @ReadOnlyComposable get() = colorScheme.secondary.copy(alpha = 0.12f)

val MaterialTheme.kbSecondaryContainer: Color
    @Composable @ReadOnlyComposable get() = colorScheme.secondaryContainer

val MaterialTheme.kbTertiary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.tertiary

val MaterialTheme.kbTertiaryBold: Color
    @Composable @ReadOnlyComposable get() = colorScheme.tertiaryContainer

val MaterialTheme.kbTertiarySubtle: Color
    @Composable @ReadOnlyComposable get() = colorScheme.tertiary.copy(alpha = 0.12f)

val MaterialTheme.kbTertiaryContainer: Color
    @Composable @ReadOnlyComposable get() = colorScheme.tertiaryContainer

val MaterialTheme.kbTextPrimary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.onBackground

val MaterialTheme.kbTextSecondary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.onSurfaceVariant

val MaterialTheme.kbTextTertiary: Color
    @Composable @ReadOnlyComposable get() = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

val MaterialTheme.kbTextOnBrand: Color
    @Composable @ReadOnlyComposable get() = colorScheme.onPrimary

val MaterialTheme.kbTextDisabled: Color
    @Composable @ReadOnlyComposable get() = colorScheme.onSurface.copy(alpha = 0.38f)

val MaterialTheme.kbOutlineSubtle: Color
    @Composable @ReadOnlyComposable get() = colorScheme.outlineVariant

val MaterialTheme.kbOutlineBold: Color
    @Composable @ReadOnlyComposable get() = colorScheme.outline

val MaterialTheme.kbBgGradient: Brush
    @Composable @ReadOnlyComposable get() = Brush.verticalGradient(
        listOf(colorScheme.background, colorScheme.surface, colorScheme.surfaceVariant)
    )

// ═══════════════════════════════════════════════════════════════
// PREMIUM TOKENS — STITCH "NOCTURNE HOSPITALITY" INSPIRED
// Glassmorphism, mesh gradients, midnight harvest palette
// ═══════════════════════════════════════════════════════════════

// ── Midnight Harvest Depth Layers (Stitch Nocturne) ──────────
val KbMidnightBase: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) Color(0xFF0A0A0A) else Color(0xFFF5F0EB)
val KbMidnightSurface: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) Color(0xFF1A1614) else Color(0xFFFDF8F4)
val KbMidnightOverlay: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) Color(0xE61A1614.toInt()) else Color(0xE6FFFFFF.toInt())

// ── Glassmorphism Surfaces ────────────────────────────────────
val KbGlassSurface: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) {
        Color(0x991C1810)
    } else {
        Color(0x99FFFFFF)
    }

val KbGlassOverlay: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) {
        Color(0xCC1A1614.toInt())
    } else {
        Color(0xCCFFFFFF.toInt())
    }

val KbGlassBorder: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) {
        Color(0x1AFFFFFF.toInt())
    } else {
        Color(0x1A000000.toInt())
    }

// ── Mesh Gradients (Stitch "glow zones") ──────────────────────
val KbMeshGradientSaffron: Brush
    @Composable @ReadOnlyComposable get() = Brush.radialGradient(
        colors = listOf(KbBrandSaffron.copy(alpha = 0.12f), KbBrandSaffron.copy(alpha = 0.04f), Color.Transparent),
        center = androidx.compose.ui.geometry.Offset(0.7f, 0.3f),
        radius = 600f
    )

val KbMeshGradientAmber: Brush
    @Composable @ReadOnlyComposable get() = Brush.radialGradient(
        colors = listOf(KbBrandSaffronLight.copy(alpha = 0.10f), Color.Transparent),
        center = androidx.compose.ui.geometry.Offset(0.3f, 0.7f),
        radius = 500f
    )

val KbMeshHeroGradient: Brush
    @Composable @ReadOnlyComposable get() = Brush.verticalGradient(
        colors = listOf(KbBrandSaffron, Color(0xFF7A3300), Color(0xFF5A2200))
    )

// ── Brand Glows ──────────────────────────────────────────────
val KbSaffronGlow: Brush
    @Composable @ReadOnlyComposable get() = Brush.radialGradient(
        colors = listOf(KbBrandSaffron.copy(alpha = 0.15f), Color.Transparent)
    )

val KbSaffronGlowStrong: Brush
    @Composable @ReadOnlyComposable get() = Brush.radialGradient(
        colors = listOf(KbBrandSaffron.copy(alpha = 0.35f), Color.Transparent)
    )

// ── Zebra Stripe Pattern (Stitch list clarity) ────────────────
val KbZebraOdd: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) Color(0xFF14110F) else Color(0xFFF5F0EB)
val KbZebraEven: Color
    @Composable @ReadOnlyComposable get() = if (globalIsDark) Color(0xFF1A1614) else Color.White

// ── Focus Glow (input borders, active states) ─────────────────
val KbFocusGlow: Color
    @Composable @ReadOnlyComposable get() = KbBrandSaffron.copy(alpha = 0.5f)

// ═══════════════════════════════════════════════════════════════
// LEGACY ALIASES — all mapped to teal + neutral grays
// ═══════════════════════════════════════════════════════════════

val DarkBrown1: Color
    get() = if (globalIsDark) Color(0xFF211A14) else Color(0xFFFFF8F6)
val DarkBrown2: Color
    get() = if (globalIsDark) Color(0xFF332A22) else Color(0xFFEADDD4)
val PrimaryGold: Color
    get() = if (globalIsDark) KbBrandSaffronLight else KbBrandSaffronAndroid
val LightGold: Color
    get() = if (globalIsDark) KbBrandSaffronLight else KbBrandSaffron
val TextGold: Color
    // Dark:  KbBrandSaffronLight #E8832A on #211A14 → 5.1:1 ✅ AA
    // Light: #7A3300 on #FFF1EB  → 6.2:1 ✅ AA  (was #C85A00 = 3.2:1 ✗)
    get() = if (globalIsDark) KbBrandSaffronLight else Color(0xFF7A3300)
val TextLight: Color
    get() = if (globalIsDark) Color(0xFFF7F3EE) else Color(0xFF241913)
val TextMuted: Color
    // Dark:  #C1B7AD on #211A14 → 5.4:1 ✅
    // Light: #5C3A1E on #FFF1EB → 5.9:1 ✅  (was #574237 = 4.8:1 borderline)
    get() = if (globalIsDark) Color(0xFFC1B7AD) else Color(0xFF5C3A1E)
val CardBG: Color
    // Dark:  #211A14 — warm dark brown card
    // Light: #FFFFFF — pure white card (pops against #F8F6F3 page background)
    get() = if (globalIsDark) Color(0xFF211A14) else Color(0xFFFFFFFF)
val BorderGold: Color
    // Dark:  saffron at 15% — subtle on dark
    // Light: saffron at 35% — visible on white  (was 25% — nearly invisible)
    get() = if (globalIsDark) KbBrandSaffronLight.copy(alpha = 0.15f) else KbBrandSaffronAndroid.copy(alpha = 0.35f)
val ParchmentBG: Color
    get() = if (globalIsDark) KbSaffron950 else KbSaffron100
val BrownSelected: Color
    get() = if (globalIsDark) KbSaffron900 else KbSaffron200
val BrandPurple: Color
    get() = if (globalIsDark) KbSaffron400 else KbSaffron600
val BrandPurpleDim: Color
    get() = if (globalIsDark) KbSaffron300 else KbSaffron400
val Brown500: Color
    get() = if (globalIsDark) Color(0xFF332A22) else Color(0xFFE8D5C8)
val DarkBrownSheet: Color
    get() = if (globalIsDark) Color(0xFF211A14) else Color(0xFFF4DED4)
val RichEspresso: Color
    get() = if (globalIsDark) Color(0xFF17130F) else Color(0xFFF8F6F3)
val BottomNavBG: Color
    // Light: match the page background exactly so nav bar blends in
    get() = if (globalIsDark) Color(0xFF17130F) else Color(0xFFF8F6F3)

// Semantic status
val VegGreen      = KbGreen
val NonVegRed     = KbRed

// Secondary green aliases for theme mapping
val KbSecondaryGreen    = KbGreenSec600  // = #3D7A5A
val KbSecondaryGreenDark = KbGreenSec500 // = #4B9A6E (brighter in dark)
val SuccessGreen  = KbGreen
val DangerRed     = KbRed
val ErrorPink     = KbRed
val WarningYellow = KbYellow
val GreenReportBg = KbGreenSubtle.copy(alpha = 0.22f)
val Green800      = KbGreen

// Payment badges
val ZomatoRed     = KbZomatoRed
val SwiggyOrange  = KbSwiggyOrange
val GoogleRed     = Color(0xFFEF4444)
val WhatsAppGreen = KbWhatsAppGreen
