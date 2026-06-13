package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.KbBrandViolet
import com.khanabook.lite.pos.ui.theme.KbMidnightGradient
import kotlin.math.min

/**
 * A beautiful, premium container Box with a deep dark purple gradient background
 * overlayed with two soft, blurred, semi-transparent purple/burgundy blobs.
 * Matches the KhanaBook premium onboarding and login flow aesthetic.
 */
@Composable
fun KhanaBookPurpleBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .background(KbMidnightGradient)
            .drawBehind {
                val width = size.width
                val height = size.height

                // Top-right blob (large soft indigo/violet circle)
                val trRadius = min(width * 0.65f, with(density) { 260.dp.toPx() })
                val trCenter = Offset(width * 0.95f, height * 0.05f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            KbBrandViolet.copy(alpha = 0.28f), // Rich indigo/violet
                            Color(0xFF4C1D95).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = trCenter,
                        radius = trRadius
                    ),
                    center = trCenter,
                    radius = trRadius
                )

                // Bottom-left blob (smaller soft burgundy/pink circle)
                val blRadius = min(width * 0.5f, with(density) { 180.dp.toPx() })
                val blCenter = Offset(width * 0.05f, height * 0.95f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9D174D).copy(alpha = 0.20f), // Warm rose/burgundy
                            Color(0xFF831843).copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = blCenter,
                        radius = blRadius
                    ),
                    center = blCenter,
                    radius = blRadius
                )
            },
        contentAlignment = contentAlignment,
        content = content
    )
}
