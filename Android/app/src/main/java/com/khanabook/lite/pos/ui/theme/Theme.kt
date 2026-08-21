package com.khanabook.lite.pos.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGold,
    secondary = LightGold,
    tertiary = TextGold,
    background = DarkBrown1,
    surface = DarkBrown2,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = TextLight,
    onSurface = TextLight,
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
    val typeScale: TypeScaleTier
        @Composable
        get() = LocalTypeScale.current
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun KhanaBookLiteTheme(
    displayScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthSizeClass = if (view.isInEditMode) {
        WindowWidthSizeClass.Compact
    } else {
        calculateWindowSizeClass(view.context as Activity).widthSizeClass
    }
    val responsiveLayout = responsiveLayoutForWindowSizeClass(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp,
        widthSizeClass = widthSizeClass
    )
    val appTypography = typographyForTier(responsiveLayout.typeScaleTier)

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
                    "widthTier=${responsiveLayout.widthTier}, " +
                    "typeScaleTier=${responsiveLayout.typeScaleTier}"
            )
        }
    }

    if (!view.isInEditMode) {

        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            // Force dark system bar icons (light-on-dark) for our dark theme.
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            // On 3-button navigation (Android 10+), the system may draw a light scrim
            // over the nav bar area. Override with transparent so our DarkBrown1 scaffold
            // shows through consistently across gesture, 3-button, and OEM variants.
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            // On API 29+ (Android 10), disable the system-drawn contrast scrim that
            // some OEMs (OPPO, Samsung) apply to the navigation bar in 3-button mode.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
    }

    // Respect the user's system font scale on all SDK levels for accessibility.
    // Android 16 (SDK 36) applies non-linear font scaling automatically — we should
    // not override it. Layouts must accommodate larger text via scrolling and wrapping.
    val effectiveFontScale = density.fontScale
    val effectiveDensity = density.density * displayScale
    CompositionLocalProvider(
        LocalDensity provides Density(density = effectiveDensity, fontScale = effectiveFontScale),
        LocalSpacing provides Spacing(),
        LocalIconSize provides IconSize(),
        LocalResponsiveLayout provides responsiveLayout,
        LocalTypeScale provides responsiveLayout.typeScaleTier
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = appTypography,
            content = content
        )
    }
}
