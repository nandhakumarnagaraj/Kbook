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
    // ── Seamless entrance (Logo starts visible to match OS splash without blinking) ──
    var textVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { textVisible = true }

    // Title fade & subtle slide
    val titleAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(350, delayMillis = 60, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (textVisible) 0f else 6f,
        animationSpec = tween(350, delayMillis = 60, easing = FastOutSlowInEasing),
        label = "titleOffsetY"
    )

    // Subtitle fade
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(350, delayMillis = 160, easing = FastOutSlowInEasing),
        label = "subtitleAlpha"
    )

    // Version fade
    val versionAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(350, delayMillis = 240, easing = FastOutSlowInEasing),
        label = "versionAlpha"
    )

    // Subtle breathing glow halo (0 GPU blur overhead, butter smooth on all devices)
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
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
            // Logo container with breathing radial halo (rock-solid, 0 flicker from system splash)
            Box(
                modifier = Modifier.size(180.dp),
                contentAlignment = Alignment.Center
            ) {
                // Feathered golden halo
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GoldPrimary.copy(alpha = glowAlpha),
                                    GoldPrimary.copy(alpha = glowAlpha * 0.35f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                // Logo — fully visible from frame 0 to match system splash seamlessly
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = "KhanaBook POS",
                    modifier = Modifier.size(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App name — KhanaBook POS
            Text(
                text = "KhanaBook POS",
                color = TextWhite,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                    translationY = titleOffsetY
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Restaurant POS & Billing",
                color = GoldPrimary.copy(alpha = subtitleAlpha * 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.8.sp
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
