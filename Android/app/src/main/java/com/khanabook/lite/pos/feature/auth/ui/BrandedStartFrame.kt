package com.khanabook.lite.pos.feature.auth.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Modern Clean Palette ──────────────────────────────────────────────────────
private val DarkBg = Color(0xFF0D0104)
private val DarkBgMid = Color(0xFF1A0A0F)
private val DarkBgWarm = Color(0xFF2D0A10)
private val GoldPrimary = Color(0xFFC8960C)
private val TextWhite = Color(0xFFF5F5F5)
private val TextMuted = Color(0xFFB0B0B0)

/**
 * Zomato-style branded splash screen — Khanabook PoS.
 *
 * Design: High-energy, competitor-inspired (Zomato benchmark) with a
 * continuous handoff from the native splash:
 * - Logo renders at full size/alpha from frame 0 (exact match of the
 *   system splash icon 150dp) so the transition is seamless — no blink,
 *   no re-fade, no re-scale collapse.
 * - After a short hold the logo does a celebratory spring "pop"
 *   (1 → 1.12 → 1) — the Zomato-style bounce — that reads as coming
 *   from the same frame as splash #1.
 * - Staggered text reveal (title → tagline → version)
 * - Subtle golden particle burst radiating from the logo
 * - Breathing radial halo behind the logo
 */
@Composable
internal fun BrandedStartFrame(modifier: Modifier = Modifier) {
    // ── Staggered entrance trigger ──
    var textVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Hold long enough for the native splash handoff to land seamlessly,
        // then run all entrance animations together.
        kotlinx.coroutines.delay(80)
        textVisible = true
    }

    // Logo: starts exactly at 1.0 alpha + 1.0 scale to match the system splash
    // icon pixel-for-pixel. A short hold, then a celebratory spring pop.
    val logoScale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(260)
        logoScale.animateTo(
            targetValue = 1.12f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f)
        )
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f)
        )
    }

    // Title fade & slide-up (stagger 280ms)
    val titleAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 280, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (textVisible) 0f else 24f,
        animationSpec = tween(450, delayMillis = 280, easing = FastOutSlowInEasing),
        label = "titleOffsetY"
    )

    // Tagline fade & slide-up (stagger 440ms)
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 440, easing = FastOutSlowInEasing),
        label = "subtitleAlpha"
    )
    val subtitleOffsetY by animateFloatAsState(
        targetValue = if (textVisible) 0f else 14f,
        animationSpec = tween(450, delayMillis = 440, easing = FastOutSlowInEasing),
        label = "subtitleOffsetY"
    )

    // Version fade (stagger 640ms)
    val versionAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(350, delayMillis = 640, easing = FastOutSlowInEasing),
        label = "versionAlpha"
    )

    // ── Breathing glow halo behind the logo ──
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
            // Logo container with breathing radial halo + particle burst
            Box(
                modifier = Modifier.size(200.dp),
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
                // Golden particle ring bursting outward from the logo
                ParticleBurst(modifier = Modifier.size(200.dp))
                // Logo — carries over from the native splash at full size, then
                // does a celebratory spring pop after a short hold
                Image(
                    painter = painterResource(id = R.drawable.khanabook_logo),
                    contentDescription = "KhanaBook POS",
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                            alpha = 1f
                        }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App name — KhanaBook POS (slide-up stagger)
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

            // Tagline (slide-up stagger)
            Text(
                text = "Restaurant POS & Billing",
                color = GoldPrimary.copy(alpha = subtitleAlpha * 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.8.sp,
                modifier = Modifier.graphicsLayer {
                    translationY = subtitleOffsetY
                }
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

/**
 * Subtle golden particle ring that bursts outward from the logo
 * and fades once during the entrance (Zomato-style firework feel).
 */
@Composable
private fun ParticleBurst(
    modifier: Modifier = Modifier,
    particleCount: Int = 12,
    color: Color = GoldPrimary
) {
    val burstProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        burstProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(950, delayMillis = 160, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier) {
        val progress = burstProgress.value
        if (progress <= 0f) return@Canvas

        val center = this.center
        val ringRadius = size.minDimension * 0.44f
        val spacing = (2f * PI / particleCount).toFloat()
        for (i in 0 until particleCount) {
            val angle = spacing * i
            val eased = FastOutSlowInEasing.transform(progress)
            val radius = ringRadius * eased
            val alpha = (1f - eased) * 0.45f
            if (alpha <= 0.01f) continue
            val dotRadius = (4.5f * (1f - eased * 0.5f)).dp.toPx()
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(
                    center.x + cos(angle) * radius,
                    center.y + sin(angle) * radius
                )
            )
        }
    }
}