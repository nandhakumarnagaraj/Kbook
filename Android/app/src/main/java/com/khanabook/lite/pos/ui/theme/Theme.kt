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

// Dark scheme — warm espresso bg + ember saffron primary
private val DarkColorScheme = darkColorScheme(
    primary            = KbSaffron400,    // #E8832A warm ember
    primaryContainer   = KbSaffron500,
    secondary          = KbEspresso900,
    tertiary           = KbSaffron400,
    background         = KbEspresso950,   // #17130F dark espresso
    surface            = KbEspresso900,   // #211A14 warm charcoal
    surfaceVariant     = Color(0xFF2C2218),
    onPrimary          = Color.White,
    onPrimaryContainer = Color.White,
    onSecondary        = Color.White,
    onTertiary         = Color.White,
    onBackground       = Color(0xE6FFFFFF.toInt()), // white-alpha-90
    onSurface          = Color(0xE6FFFFFF.toInt()),
    onSurfaceVariant   = Color(0xB3FFFFFF.toInt()), // white-alpha-70
    outline            = Color(0x1AFFFFFF.toInt()), // white-alpha-10
    outlineVariant     = Color(0x0DFFFFFF.toInt()), // white-alpha-05
)

// Light scheme — warm cream bg + deep saffron primary
private val LightColorScheme = lightColorScheme(
    primary            = KbSaffron500,    // #C85A00 deep saffron
    primaryContainer   = KbSaffron50,
    secondary          = KbEspresso100,
    tertiary           = KbSaffron500,
    background         = KbEspresso50,    // #F5EDE4 warm linen
    surface            = KbEspresso100,   // #E8D9CA tan parchment
    surfaceVariant     = KbEspresso200,   // #D4C0AB mid-tone
    onPrimary          = Color.White,
    onPrimaryContainer = KbSaffron500,
    onSecondary        = Color(0xFF17130F),
    onTertiary         = Color.White,
    onBackground       = Color(0xE6000000.toInt()), // black-alpha-90
    onSurface          = Color(0xE6000000.toInt()),
    onSurfaceVariant   = Color(0xB3000000.toInt()), // black-alpha-70
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
