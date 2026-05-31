package com.khanabook.lite.pos.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.khanabook.lite.pos.BuildConfig

// ═══════════════════════════════════════════════════════════════
// DARK SCHEME — Saffron 🟠 primary + Sage 🟢 secondary + Sky 🔵 tertiary
// Near-black bg, warm brown cards, high-contrast text
// ═══════════════════════════════════════════════════════════════
private val DarkColorScheme = darkColorScheme(
    primary            = KbBrandSaffronLight,   // #E8832A — bright saffron
    primaryContainer   = KbBrandSaffronDark,    // #994500 dark saffron
    onPrimary          = Color(0xFF1F1B18),
    onPrimaryContainer = KbSaffron100,
    secondary          = KbGreenSec500,          // #4B9A6E sage green (brighter in dark)
    secondaryContainer = KbGreenSec800,          // #1E4D32 dark sage container
    onSecondary        = KbGreenSec100,        // #E3F2E9 light text → ~8:1 on #4B9A6E
    onSecondaryContainer = KbGreenSec100,        // #E3F2E9 light text on dark container
    tertiary           = Color(0xFF7FB8F0),     // cool sky blue accent
    tertiaryContainer  = Color(0xFF1B3050),     // dark navy
    onTertiary         = Color(0xFF0A1A30),
    onTertiaryContainer = Color(0xFFD5E8FF),
    background         = Color(0xFF060604),     // near pitch black
    surface            = Color(0xFF1C1810),     // warm dark brown card
    surfaceVariant     = Color(0xFF241F14),     // elevated
    onBackground       = Color(0xFFFBF9F6),     // warm off-white body text
    onSurface          = Color(0xFFF3EDE2),     // card text
    onSurfaceVariant   = Color(0xFFD6C8C0),     // muted text
    outline            = Color(0x1AFFFFFF.toInt()),
    outlineVariant     = Color(0x0DFFFFFF.toInt()),
)

// ═══════════════════════════════════════════════════════════════
// LIGHT SCHEME — Warm off-white page + white cards + green secondary
// Stitch: "White Space First" with visible card elevation
// ═══════════════════════════════════════════════════════════════
private val LightColorScheme = lightColorScheme(
    primary            = KbBrandSaffronAndroid, // #D97706 saffron primary
    primaryContainer   = KbSaffron100,          // #FFDBCA warm peach container
    onPrimary          = Color.White,
    onPrimaryContainer = KbSaffron950,          // #331200 deep brown
    secondary          = KbGreenSec600,          // #3D7A5A warm sage green (no longer orange!)
    secondaryContainer = KbGreenSec100,          // #E3F2E9 light sage container
    onSecondary        = KbGreenSec950,        // #0A2E1A dark green → ~8:1 on #3D7A5A
    onSecondaryContainer = KbGreenSec950,        // #0A2E1A deep green text on container
    tertiary           = Color(0xFF1B5FA8),     // strong blue tertiary
    tertiaryContainer  = Color(0xFFD5E8FF),     // light blue container
    onTertiary         = Color.White,
    onTertiaryContainer = Color(0xFF001B3C),
    background         = Color(0xFFFAF7F4),     // warm off-white page — visible depth against white cards
    surface            = Color(0xFFFFFFFF),     // pure white cards — pop against warm bg
    surfaceVariant     = Color(0xFFF0EBE6),     // slightly warmer elevated surface
    onBackground       = Color(0xFF1A1510),     // near-black text
    onSurface          = Color(0xFF1A1510),
    onSurfaceVariant   = Color(0xFF5C4F42),     // readable muted
    outline            = Color(0xFFD6CDBF),
    outlineVariant     = Color(0xFFEBE3D6),
)

object KhanaBookTheme {
    val spacing: Spacing
        @Composable
        get() = LocalSpacing.current
    val iconSize: IconSize
        @Composable
        get() = LocalIconSize.current
    val layout: ResponsiveLayout
        @Composable
        get() = LocalResponsiveLayout.current
}

@Composable
fun KhanaBookLiteTheme(
    // Follow system dark/light preference by default; ThemeState.isDark can override it
    // when the user explicitly toggles the in-app theme switch.
    darkTheme: Boolean = isSystemInDarkTheme(),
    displayScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    // Sync the global dark-mode flag so legacy color aliases (DarkBrown1, CardBG, etc.)
    // resolve correctly. On first composition this seeds from the system preference;
    // after that the user's in-app toggle takes over via globalIsDark.
    val isDark = globalIsDark

    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val responsiveLayout = responsiveLayoutForWidth(configuration.screenWidthDp)
    val appTypography = typographyForSdk(Build.VERSION.SDK_INT)

    if (BuildConfig.DEBUG) {
        LaunchedEffect(
            configuration.fontScale,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.smallestScreenWidthDp,
            configuration.orientation,
            density.density,
            density.fontScale
        ) {
            Log.d(
                "UI_SCALE_DEBUG",
                "compose density/config: " +
                    "fontScale=${configuration.fontScale}, " +
                    "density=${density.density}, " +
                    "composeFontScale=${density.fontScale}, " +
                    "screenWidthDp=${configuration.screenWidthDp}, " +
                    "screenHeightDp=${configuration.screenHeightDp}, " +
                    "smallestWidthDp=${configuration.smallestScreenWidthDp}, " +
                    "orientation=${configuration.orientation}, " +
                    "widthTier=${responsiveLayout.widthTier}"
            )
        }
    }

    // Seed globalIsDark from system preference ONLY on the very first launch
    // (i.e. when the user has never explicitly saved a preference).
    // If the user has already toggled Dark Mode in Settings, their saved value
    // in SharedPreferences is loaded by MainActivity.onCreate into globalIsDark
    // before this composable runs — we must NOT overwrite it here.
    // We detect "first launch" by checking whether the prefs key exists.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("session_prefs", android.content.Context.MODE_PRIVATE)
        val hasUserPref = prefs.contains("is_dark_theme")
        if (!hasUserPref) {
            // First launch — follow the system setting and persist it
            globalIsDark = darkTheme
            prefs.edit().putBoolean("is_dark_theme", darkTheme).apply()
        }
        // else: MainActivity.onCreate already loaded the user's saved preference
        // into globalIsDark — leave it alone.
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    val effectiveFontScale = if (Build.VERSION.SDK_INT >= 36) 1f else density.fontScale
    val effectiveDensity = density.density * displayScale
    CompositionLocalProvider(
        LocalDensity provides Density(density = effectiveDensity, fontScale = effectiveFontScale),
        LocalSpacing provides Spacing(),
        LocalIconSize provides IconSize(),
        LocalResponsiveLayout provides responsiveLayout
    ) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography = appTypography,
            shapes = KhanaShapes,
            content = content
        )
    }
}
