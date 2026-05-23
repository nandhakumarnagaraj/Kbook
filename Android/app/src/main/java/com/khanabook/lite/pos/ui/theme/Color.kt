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
// KHANBOOK DLS — BRAND PALETTE (from #C85A00 deep saffron)
// 11-shade scale generated via HSL hue(27°), lightness/saturation
// stepped per Material Design 3 shade table.
// ═══════════════════════════════════════════════════════════════

// Saffron scale — 11-shade brand progression
val KbSaffron50  = Color(0xFFFFF4EA)  // warm white tint (L97%, S80%)
val KbSaffron100 = Color(0xFFFDE5CE)  // cream (L94%, S80%)
val KbSaffron200 = Color(0xFFFAC99E)  // light apricot (L87%, S85%)
val KbSaffron300 = Color(0xFFF0A65E)  // amber (L75%, S90%)
val KbSaffron400 = Color(0xFFE4862E)  // burnt saffron (L62%, S95%)
val KbSaffron500 = Color(0xFFD07318)  // bold saffron (L48%) — dark mode primary
val KbSaffron600 = Color(0xFFC85A00)  // BRAND BASELINE (L39%) — light mode primary
val KbSaffron700 = Color(0xFFA04500)  // deep saffron (L33%)
val KbSaffron800 = Color(0xFF7A3400)  // burnt deep (L27%)
val KbSaffron900 = Color(0xFF552300)  // espresso saffron (L20%)
val KbSaffron950 = Color(0xFF301200)  // near black with warmth (L10%)

// Warm neutral scale — subtle warmth for premium feel
val KbWarmWhite   = Color(0xFFFAFAF8)
val KbWarmGray50  = Color(0xFFF5F5F3)
val KbWarmGray100 = Color(0xFFEEEEEC)
val KbWarmGray200 = Color(0xFFE0E0DD)
val KbWarmGray800 = Color(0xFF2A2A28)
val KbWarmGray900 = Color(0xFF1C1C1A)
val KbWarmGray950 = Color(0xFF11110F)

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

val MaterialTheme.kbBgGradient: Brush
    @Composable @ReadOnlyComposable get() = Brush.verticalGradient(
        listOf(colorScheme.background, colorScheme.surface, colorScheme.surfaceVariant)
    )

// ═══════════════════════════════════════════════════════════════
// LEGACY ALIASES — Concrete Color Constants
//
// These map to the new DLS palette values (light-mode equivalents).
// They compile in all contexts (default params, static objects, etc).
// Gradually migrate call-sites to MaterialTheme.kbXxx extensions.
// ═══════════════════════════════════════════════════════════════

val DarkBrown1    = KbWarmGray900  // neutral dark surface
val DarkBrown2    = KbWarmGray800  // neutral darker surface
val PrimaryGold   = KbSaffron600   // brand primary
val LightGold     = KbSaffron400   // brand lighter variant
val TextGold      = KbSaffron600   // brand text colour
val TextLight     = Color(0xFFF5F5F3)  // near-white for text on dark
val TextMuted     = Color(0xFFB0B0AE)  // muted for placeholders on dark
val CardBG        = KbWarmGray900  // card background
val BorderGold    = KbSaffron600.copy(alpha = 0.15f)
val ParchmentBG   = KbSaffron50    // warm tinted bg
val BrownSelected = KbSaffron50    // selection tint
val BrandPurple   = KbSaffron600   // alias
val BrandPurpleDim = KbSaffron400
val Brown500      = KbWarmGray800
val DarkBrownSheet = KbWarmGray950
val RichEspresso  = KbWarmGray950  // neutral page bg

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
