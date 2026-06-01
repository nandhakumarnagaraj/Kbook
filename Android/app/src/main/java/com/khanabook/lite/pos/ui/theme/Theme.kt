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
// DARK SCHEME — Premium Saffron 🟠 primary + dark neutral surfaces
// #121212 bg, #1E1E1E cards, high-contrast text
// ═══════════════════════════════════════════════════════════════
private val DarkColorScheme = darkColorScheme(
    primary            = Color(0xFFFB923C),     // bright saffron primary
    primaryContainer   = Color(0xFF3B2415),     // dark saffron container
    onPrimary          = Color(0xFF1F1B18),
    onPrimaryContainer = KbSaffron100,
    secondary          = KbBrandSaffronDark,    // #D97706 deeper saffron
    secondaryContainer = Color(0xFF2A1F0D),     // dark amber container
    onSecondary        = KbSaffron100,
    onSecondaryContainer = KbSaffron100,
    tertiary           = Color(0xFF60A5FA),     // soft blue accent
    tertiaryContainer  = Color(0xFF1B3050),
    onTertiary         = Color(0xFF0A1A30),
    onTertiaryContainer = Color(0xFFD5E8FF),
    background         = Color(0xFF121212),
    surface            = Color(0xFF1E1E1E),
    surfaceVariant     = Color(0xFF252525),
    onBackground       = Color(0xFFF5F5F5),
    onSurface          = Color(0xFFF5F5F5),
    onSurfaceVariant   = Color(0xFFB3B3B3),
    outline            = Color(0x1AFFFFFF.toInt()),
    outlineVariant     = Color(0x0DFFFFFF.toInt()),
    error              = Color(0xFFF87171),
    onError            = Color(0xFF1A0000),
    errorContainer     = Color(0xFF93000A),
    onErrorContainer   = Color(0xFFFFDAD6),
)

// ═══════════════════════════════════════════════════════════════
// LIGHT SCHEME — Premium Saffron 🟠 primary + warm neutral surfaces
// #FAF8F5 page, white cards, slate text
// ═══════════════════════════════════════════════════════════════
private val LightColorScheme = lightColorScheme(
    primary            = KbBrandSaffron,        // #F97316 saffron primary
    primaryContainer   = Color(0xFFFFF0E6),     // warm peach container
    onPrimary          = Color.White,
    onPrimaryContainer = Color(0xFF3B1A00),     // deep brown
    secondary          = KbBrandSaffronDark,    // #D97706 deeper saffron
    secondaryContainer = Color(0xFFFFF0E6),
    onSecondary        = Color.White,
    onSecondaryContainer = Color(0xFF3B1A00),
    tertiary           = Color(0xFF2563EB),     // strong blue
    tertiaryContainer  = Color(0xFFD5E8FF),
    onTertiary         = Color.White,
    onTertiaryContainer = Color(0xFF001B3C),
    background         = Color(0xFFFAF8F5),     // warm off-white
    surface            = Color(0xFFFFFFFF),     // white cards
    surfaceVariant     = Color(0xFFF5F0EB),     // slightly warmer
    onBackground       = Color(0xFF1F2937),     // slate-800
    onSurface          = Color(0xFF1F2937),
    onSurfaceVariant   = Color(0xFF6B7280),     // slate-500
    outline            = Color(0xFFD1D5DB),     // gray-300
    outlineVariant     = Color(0xFFE5E7EB),     // gray-200
    error              = Color(0xFFDC2626),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFE6E6),
    onErrorContainer   = Color(0xFF7F0000),
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
