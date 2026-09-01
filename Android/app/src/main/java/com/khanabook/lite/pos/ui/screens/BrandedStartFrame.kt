package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R

// ── Modern Clean Palette ──────────────────────────────────────────────────────
private val DarkBg = Color(0xFF0D0104)
private val DarkBgMid = Color(0xFF1A0A0F)
private val DarkBgWarm = Color(0xFF2D0A10)
private val GoldPrimary = Color(0xFFC8960C)
private val TextWhite = Color(0xFFF5F5F5)
private val TextMuted = Color(0xFFB0B0B0)

/**
 * Modern branded splash screen — Khanabook PoS.
 *
 * Design: Clean, minimal, competitor-inspired:
 * - Simple logo with subtle glow
 * - Clean text with minimal animation
 * - Fast fade-in (no heavy animations)
 * - Version at bottom only
 * - No trust badges or company info on splash
 */
@Composable
internal fun BrandedStartFrame(modifier: Modifier = Modifier) {
    // ── Simple fade-in animation (fast, clean) ────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Logo fade + subtle scale
    val logoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "logoScale"
    )

    // Title fade
    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )

    // Subtitle fade
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "subtitleAlpha"
    )

    // Version fade
    val versionAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "versionAlpha"
    )

    // Subtle pulsing glow (infinite, gentle)
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // ── Layout ─────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBg,
                        DarkBgMid,
                        DarkBgWarm,
                        DarkBgMid,
                        DarkBg
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // ── Center: Logo + Text ────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Logo with subtle glow (fixed: outer box is now larger to contain glow)
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                    },
                contentAlignment = Alignment.Center
            ) {
                // Soft glow behind logo
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .blur(25.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GoldPrimary.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = "Khanabook PoS",
                    modifier = Modifier.size(110.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "Khanabook PoS",
                color = TextWhite,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Restaurant POS & Billing",
                color = GoldPrimary.copy(alpha = subtitleAlpha),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp
            )
        }

        // ── Bottom: Version only ───────────────────────────────────────────────
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            color = TextMuted.copy(alpha = versionAlpha * 0.5f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}
