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

// Dark scheme — warm-tinted dark bg + white text + bold saffron primary
private val DarkColorScheme = darkColorScheme(
    primary            = KbSaffron500,    // #D07318 bold saffron — brighter for dark bg
    primaryContainer   = KbSaffron800,    // #7A3400 burnt deep
    secondary          = KbWarmGray800,   // #2A2A28 warm charcoal
    tertiary           = KbSaffron400,    // #E4862E burnt saffron accent
    background         = KbWarmGray950,   // #11110F near black with warmth
    surface            = KbWarmGray900,   // #1C1C1A card surface
    surfaceVariant     = KbWarmGray800,   // #2A2A28 elevated surface
    onPrimary          = Color.White,
    onPrimaryContainer = KbSaffron50,     // warm white on burnt container
    onSecondary        = KbWarmGray50,
    onTertiary         = Color.White,
    onBackground       = KbWarmGray50,    // #F5F5F3 off-white body text
    onSurface          = KbWarmGray100,   // #EEEEEC card text
    onSurfaceVariant   = Color(0xFFB0B0AE), // muted text
    outline            = Color(0x1AFFFFFF.toInt()), // white-alpha-10
    outlineVariant     = Color(0x0DFFFFFF.toInt()), // white-alpha-05
)

// Light scheme — warm white bg + dark warm text + deep saffron primary
private val LightColorScheme = lightColorScheme(
    primary            = KbSaffron600,    // #C85A00 BRAND — deep saffron
    primaryContainer   = KbSaffron50,     // #FFF4EA warm white tint
    secondary          = KbWarmGray100,   // #EEEEEC warm light
    tertiary           = KbSaffron600,    // #C85A00 accent
    background         = Color.White,     // pure white bg
    surface            = KbWarmGray50,    // #F5F5F3 warm card surface
    surfaceVariant     = KbWarmGray100,   // #EEEEEC elevated surface
    onPrimary          = Color.White,
    onPrimaryContainer = KbSaffron900,    // #552300 dark saffron
    onSecondary        = KbWarmGray950,   // #11110F near black
    onTertiary         = Color.White,
    onBackground       = KbWarmGray950,   // #11110F body text (warm near-black)
    onSurface          = KbWarmGray950,   // #11110F card text
    onSurfaceVariant   = Color(0xFF555553), // muted text
    outline            = Color(0x1A000000.toInt()), // black-alpha-10
    outlineVariant     = Color(0x0D000000.toInt()), // black-alpha-05
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
