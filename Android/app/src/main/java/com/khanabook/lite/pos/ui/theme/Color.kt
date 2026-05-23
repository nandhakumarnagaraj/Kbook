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
// KHANBOOK DLS — TEAL BRAND PALETTE (from #0891B2)
// 11-shade scale, hue 192° cyan-teal
// ═══════════════════════════════════════════════════════════════

// Teal brand scale
val KbTeal50  = Color(0xFFECF9FB)
val KbTeal100 = Color(0xFFD2F1F6)
val KbTeal200 = Color(0xFFA5E4ED)
val KbTeal300 = Color(0xFF67D5E5)
val KbTeal400 = Color(0xFF22C3D9)
val KbTeal500 = Color(0xFF0AA5BE)
val KbTeal600 = Color(0xFF0891B2)  // BRAND
val KbTeal700 = Color(0xFF067A95)
val KbTeal800 = Color(0xFF056074)
val KbTeal900 = Color(0xFF044A5A)
val KbTeal950 = Color(0xFF023241)

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

// Semantic status
val KbGreen   = Color(0xFF16A34A)
val KbGreenSubtle  = Color(0xFFF0FDF4)
val KbGreenDark    = Color(0xFF052E16)
val KbRed     = Color(0xFFDC2626)
val KbRedSubtle    = Color(0xFFFEF2F2)
val KbRedDark      = Color(0xFF450A0A)
val KbYellow  = Color(0xFFD97706)
val KbYellowSubtle = Color(0xFFFFFBEB)
val KbYellowDark   = Color(0xFF451A03)
val KbBlue    = Color(0xFF0284C7)
val KbBlueSubtle   = Color(0xFFF0F9FF)
val KbBlueDark     = Color(0xFF082F49)

// Payment brand colours
val KbZomatoRed   = Color(0xFFEF4444)
val KbSwiggyOrange = Color(0xFFF97316)
val KbWhatsAppGreen = Color(0xFF22C55E)

// ═══════════════════════════════════════════════════════════════
// THEME STATE
// ═══════════════════════════════════════════════════════════════
object ThemeState {
    var isDark: Boolean by mutableStateOf(true)
}

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
// LEGACY ALIASES — all mapped to teal + neutral grays
// ═══════════════════════════════════════════════════════════════

val DarkBrown1    = KbGray900     // neutral dark surface
val DarkBrown2    = KbGray800     // neutral darker surface
val PrimaryGold   = KbTeal600     // brand primary (was saffron)
val LightGold     = KbTeal400     // brand lighter variant
val TextGold      = KbTeal600     // brand text colour
val TextLight     = Color(0xFFF0F0F0)   // near-white for text on dark
val TextMuted     = Color(0xFFB0B0B0)   // muted for placeholders on dark
val CardBG        = KbGray900     // card background (dark)
val BorderGold    = KbTeal600.copy(alpha = 0.15f)
val ParchmentBG   = KbTeal50      // tinted bg → teal tint
val BrownSelected = KbTeal50      // selection tint → teal tint
val BrandPurple   = KbTeal600     // alias
val BrandPurpleDim = KbTeal400
val Brown500      = KbGray800
val DarkBrownSheet = KbGray950
val RichEspresso  = KbGray950     // neutral page bg

// Semantic status
val VegGreen      = KbGreen
val NonVegRed     = KbRed
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
