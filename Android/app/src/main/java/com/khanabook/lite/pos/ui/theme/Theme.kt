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

// Dark scheme — warm dark charcoal bg + warm white text + amber/saffron primary
private val DarkColorScheme = darkColorScheme(
    primary            = KbSaffron400,   // #FBBF24 — bright amber for dark bg
    primaryContainer   = KbSaffron900,   // #78350F dark amber
    secondary          = Color(0xFF2B2521),
    tertiary           = KbSaffron400,
    background         = Color(0xFF141210),   // warm black
    surface            = Color(0xFF1F1B18),   // warm dark brown card
    surfaceVariant     = Color(0xFF2B2521),   // elevated
    onPrimary          = Color(0xFF1F1B18),
    onPrimaryContainer = KbSaffron50,
    onSecondary        = Color.White,
    onTertiary         = Color.White,
    onBackground       = Color(0xFFFBF9F6),   // warm off-white body text
    onSurface          = Color(0xFFF3EDE2),   // card text
    onSurfaceVariant   = Color(0xFFD6C8C0),   // muted text
    outline            = Color(0x1AFFFFFF.toInt()),
    outlineVariant     = Color(0x0DFFFFFF.toInt()),
)

// Light scheme — warm milk-white bg + near-black text + saffron primary
private val LightColorScheme = lightColorScheme(
    primary            = KbSaffron600,   // #D97706 BRAND
    primaryContainer   = KbSaffron50,    // #FFFDF5 soft cream tint
    secondary          = Color(0xFFF3EDE2),
    tertiary           = KbSaffron600,
    background         = Color(0xFFFFFDF9),   // warm milk white bg
    surface            = Color(0xFFFAF6F0),   // warm light cream card surface
    surfaceVariant     = Color(0xFFF3EDE2),   // elevated
    onPrimary          = Color.White,
    onPrimaryContainer = KbSaffron900,   // #78350F dark saffron
    onSecondary        = Color.Black,
    onTertiary         = Color.White,
    onBackground       = Color(0xFF1F1B18),   // dark brown-black body text
    onSurface          = Color(0xFF1F1B18),
    onSurfaceVariant   = Color(0xFF8C7D75),   // warm muted text
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
