@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.LightGold
import com.khanabook.lite.pos.ui.theme.TextLight

// ── Rich Burgundy Palette ──────────────────────────────────────────────────────
private val BurgundyDeep = Color(0xFF0D0104)
private val BurgundyTop = Color(0xFF170207)
private val BurgundyMid = Color(0xFF2D0A10)
private val BurgundyWarm = Color(0xFF3A0E15)
private val BurgundyBottom = Color(0xFF120205)
private val GoldPrimary = Color(0xFFC8960C)
private val GoldLight = Color(0xFFE8B84A)
private val GoldShimmer = Color(0xFFFFD700)
private val GoldSubtle = Color(0xFF9B7B2F)
private val TrustGreen = Color(0xFF4CAF50)

/**
 * Premium branded splash screen — KhanaBook.
 *
 * Design: Paytm-style single branded screen with:
 * - Scale-up logo with pulsing radial glow
 * - Gold shimmer sweep on title text
 * - Staggered fade+slide animations
 * - Trust/security indicators at bottom
 * - App version + loading dot animation
 * - Rich multi-stop burgundy gradient
 */
@Composable
internal fun BrandedStartFrame(modifier: Modifier = Modifier) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    // ── Animation triggers ─────────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Logo: scale from 0.8 → 1.0 with overshoot
    val logoScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    // Title: fade + slide up
    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(500, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "titleOffset"
    )

    // Subtitle
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(450, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "subtitleAlpha"
    )

    // Footer + trust section
    val footerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "footerAlpha"
    )

    // Pulsing glow behind logo (infinite)
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Loading dots animation
    val dotsTransition = rememberInfiniteTransition(label = "dots")
    val dotProgress by dotsTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotProgress"
    )

    // Gold shimmer sweep on title
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // ── Layout ─────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BurgundyDeep,
                        BurgundyTop,
                        BurgundyMid,
                        BurgundyWarm,
                        BurgundyMid,
                        BurgundyBottom,
                        BurgundyDeep
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Center: Logo + Title + Subtitle ────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 40.dp)
        ) {
            // Logo with pulsing radial glow + scale animation
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
                // Outer glow ring (pulsing)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .blur(30.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GoldPrimary.copy(alpha = glowAlpha),
                                    GoldLight.copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                // Inner glow
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    LightGold.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
                // Logo image
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = "KhanaBook",
                    modifier = Modifier.size(120.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // "KHANABOOK" with shimmer effect
            Box {
                Text(
                    text = "KHANABOOK",
                    color = TextLight,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = titleAlpha
                            translationY = titleOffset
                        }
                )
                // Shimmer overlay
                if (titleAlpha > 0.9f) {
                    Text(
                        text = "KHANABOOK",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = Color.Transparent,
                        modifier = Modifier
                            .graphicsLayer { alpha = 0.4f }
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        GoldShimmer.copy(alpha = 0.6f),
                                        Color.Transparent
                                    ),
                                    startX = shimmerOffset * 300f,
                                    endX = shimmerOffset * 300f + 150f
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle with gold gradient text effect
            Text(
                text = "Restaurant POS & Billing",
                color = GoldPrimary.copy(alpha = subtitleAlpha),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Loading indicator (3 animated dots)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(subtitleAlpha)
            ) {
                repeat(3) { index ->
                    val dotAlpha = if (dotProgress.toInt() >= index) 0.9f else 0.2f
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                GoldPrimary.copy(alpha = dotAlpha),
                                CircleShape
                            )
                    )
                }
            }
        }

        // ── Bottom: Trust Indicators + Company + Version ───────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .graphicsLayer { alpha = footerAlpha }
        ) {
            // Trust badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrustBadge(text = "OFFLINE\nFIRST")
                // Gold divider dot
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(GoldSubtle, CircleShape)
                )
                TrustBadge(text = "BANK-GRADE\nSECURITY")
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(GoldSubtle, CircleShape)
                )
                TrustBadge(text = "DATA\nPROTECTED")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Gold thin divider
            HorizontalDivider(
                modifier = Modifier.width(180.dp),
                thickness = 0.5.dp,
                color = GoldSubtle.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Company branding
            Text(
                text = "From",
                color = TextLight.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Piquant Consultancy Services",
                color = TextLight.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "P R I V A T E   L I M I T E D",
                color = TextLight.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // App version
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = GoldSubtle.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
private fun TrustBadge(text: String) {
    Text(
        text = text,
        color = GoldSubtle.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        letterSpacing = 0.5.sp
    )
}
