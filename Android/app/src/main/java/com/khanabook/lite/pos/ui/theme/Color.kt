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
val KbSaffron50  = Color(0xFFFFFDF5)  // very soft warm cream tint
val KbSaffron100 = Color(0xFFFFFBEB)  // soft amber tint
val KbSaffron200 = Color(0xFFFEF3C7)
val KbSaffron300 = Color(0xFFFDE68A)
val KbSaffron400 = Color(0xFFFBBF24)  // bright gold/saffron for dark theme
val KbSaffron500 = Color(0xFFF59E0B)  // amber
val KbSaffron600 = Color(0xFFD97706)  // BRAND SAFFRON
val KbSaffron700 = Color(0xFFB45309)
val KbSaffron800 = Color(0xFF92400E)
val KbSaffron900 = Color(0xFF78350F)
val KbSaffron950 = Color(0xFF451A03)

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

val DarkBrown1: Color
    get() = if (ThemeState.isDark) Color(0xFF1F1B18) else Color.White     // warm dark surface / light white surface
val DarkBrown2: Color
    get() = if (ThemeState.isDark) Color(0xFF2B2521) else Color(0xFFEBE8DF)     // warm darker/lighter surface
val PrimaryGold: Color
    get() = if (ThemeState.isDark) KbSaffron400 else KbSaffron600     // brand primary saffron
val LightGold: Color
    get() = if (ThemeState.isDark) KbSaffron300 else KbSaffron500     // brand lighter variant
val TextGold: Color
    get() = if (ThemeState.isDark) KbSaffron300 else KbSaffron700     // brand text colour
val TextLight: Color
    get() = if (ThemeState.isDark) Color(0xFFFBF9F6) else Color(0xFF1F1B18)   // near-white / near-black text
val TextMuted: Color
    get() = if (ThemeState.isDark) Color(0xFFD6C8C0) else Color(0xFF8C7D75)   // warm muted text
val CardBG: Color
    get() = if (ThemeState.isDark) Color(0xFF1F1B18) else Color.White     // card background
val BorderGold: Color
    get() = if (ThemeState.isDark) KbSaffron400.copy(alpha = 0.15f) else KbSaffron600.copy(alpha = 0.15f)
val ParchmentBG: Color
    get() = if (ThemeState.isDark) KbSaffron950 else KbSaffron100      // tinted bg
val BrownSelected: Color
    get() = if (ThemeState.isDark) KbSaffron900 else KbSaffron200     // selection tint
val BrandPurple: Color
    get() = if (ThemeState.isDark) KbSaffron400 else KbSaffron600     // alias
val BrandPurpleDim: Color
    get() = if (ThemeState.isDark) KbSaffron300 else KbSaffron400
val Brown500: Color
    get() = if (ThemeState.isDark) Color(0xFF2B2521) else Color(0xFFEBE8DF)
val DarkBrownSheet: Color
    get() = if (ThemeState.isDark) Color(0xFF141210) else Color(0xFFF2F0EB)
val RichEspresso: Color
    get() = if (ThemeState.isDark) Color(0xFF141210) else Color(0xFFF2F0EB)     // neutral page bg (mid-white)

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
