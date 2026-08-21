@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.LightGold
import com.khanabook.lite.pos.ui.theme.TextLight

private val BurgundyTop = Color(0xFF170207)
private val BurgundyMid = Color(0xFF2D0A10)
private val BurgundyBottom = Color(0xFF150206)
private val GoldAccent = Color(0xFFC8960C)

/**
 * Full branded splash screen matching the KhanaBook brand design:
 * Logo (with glow) → KHANABOOK → Restaurant POS & Billing → Company name
 * 
 * Shown briefly after system splash while transition to destination occurs.
 * Staggered fade-in animation for premium feel.
 */
@Composable
internal fun BrandedStartFrame(modifier: Modifier = Modifier) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val logoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(450, delayMillis = 100, easing = FastOutSlowInEasing)
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(450, delayMillis = 200, easing = FastOutSlowInEasing)
    )
    val footerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(450, delayMillis = 350, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BurgundyTop, BurgundyMid, BurgundyBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Main content — logo + name + tagline (slightly above center)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = spacing.huge)
        ) {
            // Logo with subtle glow
            Box(
                modifier = Modifier
                    .size(layout.heroImageSize + spacing.large)
                    .graphicsLayer { alpha = logoAlpha },
                contentAlignment = Alignment.Center
            ) {
                // Radial glow behind logo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    LightGold.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // KHANABOOK
            Text(
                text = "KHANABOOK",
                color = TextLight.copy(alpha = titleAlpha),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(spacing.small))

            // Restaurant POS & Billing
            Text(
                text = "Restaurant POS & Billing",
                color = GoldAccent.copy(alpha = subtitleAlpha),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal
            )
        }

        // Footer — company branding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.extraLarge)
                .graphicsLayer { alpha = footerAlpha }
        ) {
            // Divider line with "From"
            Text(
                text = "From",
                color = TextLight.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Light
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = "Piquant Consultancy Services",
                color = TextLight.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "P r i v a t e   L i m i t e d",
                color = TextLight.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
