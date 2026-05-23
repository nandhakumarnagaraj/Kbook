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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.khanabook.lite.pos.BuildConfig

// Dark scheme — near-black bg + off-white text + bright teal primary
private val DarkColorScheme = darkColorScheme(
    primary            = KbTeal400,      // #22C3D9 — bright teal for dark bg
    primaryContainer   = KbTeal900,      // #044A5A dark teal
    secondary          = KbGray800,      // #303030
    tertiary           = KbTeal400,      // #22C3D9
    background         = KbGray950,      // #0D0D0D page bg
    surface            = KbGray900,      // #212121 card surface
    surfaceVariant     = KbGray800,      // #303030 elevated
    onPrimary          = Color.White,
    onPrimaryContainer = KbTeal50,       // #ECF9FB teal-tinted white
    onSecondary        = Color.White,
    onTertiary         = Color.White,
    onBackground       = Color(0xFFF0F0F0),   // off-white body text
    onSurface          = Color(0xFFE0E0E0),   // card text
    onSurfaceVariant   = Color(0xFFB0B0B0),   // muted text
    outline            = Color(0x1AFFFFFF.toInt()),
    outlineVariant     = Color(0x0DFFFFFF.toInt()),
)

// Light scheme — white bg + near-black text + teal primary
private val LightColorScheme = lightColorScheme(
    primary            = KbTeal600,      // #0891B2 BRAND
    primaryContainer   = KbTeal50,       // #ECF9FB teal tint
    secondary          = KbGray100,      // #F0F0F0
    tertiary           = KbTeal600,      // #0891B2
    background         = Color.White,    // pure white bg
    surface            = KbGray50,       // #F8F9FA card surface
    surfaceVariant     = KbGray100,      // #F0F0F0 elevated
    onPrimary          = Color.White,
    onPrimaryContainer = KbTeal900,      // #044A5A dark teal
    onSecondary        = Color.Black,
    onTertiary         = Color.White,
    onBackground       = Color(0xFF0D0D0D),   // near-black body text
    onSurface          = Color(0xFF0D0D0D),   // card text
    onSurfaceVariant   = Color(0xFF555555),   // muted text
    outline            = Color(0x1A000000.toInt()),
    outlineVariant     = Color(0x0D000000.toInt()),
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
    displayScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
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

    val isDark = ThemeState.isDark

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
