package com.khanabook.lite.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// KHANBOOK DLS — STATIC PALETTE CONSTANTS
// These are always-available concrete Color values.
// Use in darkColorScheme(), default parameters, non-composable code.
// ═══════════════════════════════════════════════════════════════

// Saffron scale
val KbSaffron50  = Color(0xFFFDF0E6)
val KbSaffron400 = Color(0xFFE8832A)  // warm ember (dark mode primary)
val KbSaffron500 = Color(0xFFC85A00)  // deep saffron (light mode primary)
val KbSaffron600 = Color(0xFF994500)

// Espresso scale
val KbEspresso50  = Color(0xFFF7F3EE) // page bg (light)
val KbEspresso100 = Color(0xFFEAE3DC) // surface (light)
val KbEspresso900 = Color(0xFF211A14) // card/surface (dark)
val KbEspresso950 = Color(0xFF17130F) // page bg (dark)

// Semantic status (stable across themes)
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

// Payment brand colours (fixed — never themed)
val KbZomatoRed   = Color(0xFFEF4444)
val KbSwiggyOrange = Color(0xFFF97316)
val KbWhatsAppGreen = Color(0xFF22C55E)

// ═══════════════════════════════════════════════════════════════
// THEME STATE
// Kept for backward-compat with MainActivity and AppLockConfigSection.
// isDark drives the SideEffect in Theme.kt (status bar icons).
// ═══════════════════════════════════════════════════════════════
object ThemeState {
    var isDark: Boolean by mutableStateOf(true)
}

// ═══════════════════════════════════════════════════════════════
// KHANBOOK DLS — COMPOSE EXTENSION PROPERTIES
// Use these in @Composable functions for theme-aware colors.
// They read from MaterialTheme.colorScheme which is wired to the
// DayNight XML theme system in Theme.kt.
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

// ═══════════════════════════════════════════════════════════════
// LEGACY ALIASES — Concrete Color Constants
//
// These map to the new DLS palette values (light-mode equivalents).
// They compile in all contexts (default params, static objects, etc).
// Gradually migrate call-sites to MaterialTheme.kbXxx extensions.
// ═══════════════════════════════════════════════════════════════

val DarkBrown1    = KbEspresso950   // was dark page bg → espresso-950
val DarkBrown2    = KbEspresso900   // was dark card bg → espresso-900
val PrimaryGold   = KbSaffron500    // brand primary
val LightGold     = KbSaffron400    // brand primary (lighter variant)
val TextGold      = KbSaffron500    // brand text colour
val TextLight     = Color(0xFFE6FFFFFF.toInt())  // near-white for text on dark
val TextMuted     = Color(0x80FFFFFF.toInt())    // muted white for placeholders on dark
val CardBG        = KbEspresso900   // card background
val BorderGold    = KbSaffron500.copy(alpha = 0.15f)
val ParchmentBG   = KbSaffron50     // warm tinted bg
val BrownSelected = KbSaffron50     // selection tint
val BrandPurple   = KbSaffron500    // alias from purple era
val BrandPurpleDim = KbSaffron400
val Brown500      = KbEspresso900
val DarkBrownSheet = KbEspresso950
val RichEspresso  = KbEspresso950

// Semantic status — kept stable
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
