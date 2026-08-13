package com.khanabook.lite.pos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val PoppinsFont = GoogleFont("Poppins")

private val AppFontFamily = FontFamily(
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = PoppinsFont, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
)

private val BaseTypography = Typography(
    // Display - Reserved for Large UI elements (e.g. Dashboard headers)
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // Headlines - Main screen titles
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // Titles - Section headers / Card titles
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body - Main readable content
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Labels - Small text, buttons, tags
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Two-zone controlled scaling per tier: headings (display/headline/title) scale a
// little more than body/labels. Discrete breakpoints only — never continuous.
//
// These are intentional empirical design-system values chosen for the KhanaBook POS
// interface. They preserve visual hierarchy across device sizes:
//   - CompactPhone (0.90/0.95): Reduces text modestly on small/cramped screens to
//     ensure all dashboard elements fit without scrolling.
//   - MediumPhone (1.00/1.00): Baseline — Material 3 default sizes.
//   - LargePhone (1.08/1.04): Headings grow 8% for stronger visual anchoring on
//     larger screens; body grows only 4% to avoid excessive density loss.
//   - Tablet (1.18/1.08): Headings grow 18% to maintain visual prominence on
//     wide screens; body grows 8% for comfortable reading at tablet distances.
//
// The heading:body ratio gap (10-14%) reinforces visual hierarchy at every tier.
// These values should be validated empirically on target devices before changing.
private val TypeTierScales: Map<TypeScaleTier, Pair<Float, Float>> = mapOf(
    TypeScaleTier.CompactPhone to (0.90f to 0.95f),
    TypeScaleTier.MediumPhone to (1.00f to 1.00f),
    TypeScaleTier.LargePhone to (1.08f to 1.04f),
    TypeScaleTier.Tablet to (1.18f to 1.08f)
)

fun typographyForTier(tier: TypeScaleTier): Typography {
    val (headingScale, textScale) = TypeTierScales[tier] ?: (1.00f to 1.00f)
    return BaseTypography.tiered(headingScale, textScale)
}

private fun Typography.tiered(headingScale: Float, textScale: Float): Typography = copy(
    displayLarge = displayLarge.at(headingScale),
    displayMedium = displayMedium.at(headingScale),
    displaySmall = displaySmall.at(headingScale),
    headlineLarge = headlineLarge.at(headingScale),
    headlineMedium = headlineMedium.at(headingScale),
    headlineSmall = headlineSmall.at(headingScale),
    titleLarge = titleLarge.at(headingScale),
    titleMedium = titleMedium.at(headingScale),
    titleSmall = titleSmall.at(headingScale),
    bodyLarge = bodyLarge.at(textScale),
    bodyMedium = bodyMedium.at(textScale),
    bodySmall = bodySmall.at(textScale),
    labelLarge = labelLarge.at(textScale),
    labelMedium = labelMedium.at(textScale),
    labelSmall = labelSmall.at(textScale)
)

private fun TextStyle.at(scale: Float): TextStyle = copy(
    fontSize = fontSize.at(scale),
    lineHeight = lineHeight.at(scale)
)

private fun TextUnit.at(scale: Float): TextUnit {
    return if (isSp) (value * scale).sp else this
}
