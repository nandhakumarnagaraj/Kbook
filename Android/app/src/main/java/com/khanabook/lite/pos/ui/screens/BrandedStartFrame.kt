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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.LightGold
import com.khanabook.lite.pos.ui.theme.TextLight

private val BurgundyTop = Color(0xFF170207)
private val BurgundyMid = Color(0xFF2D0A10)
private val BurgundyBottom = Color(0xFF150206)

/**
 * Seamless branded first frame shown right after the native splash while the
 * startup routing decision completes. Released the instant the decision is
 * ready (zero artificial delay); the native splash background blends into
 * this frame's gradient so startup reads as one continuous screen.
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
    val textAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 150, easing = FastOutSlowInEasing)
    )
    val footerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, delayMillis = 300, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BurgundyTop, BurgundyMid, BurgundyBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -(spacing.extraLarge))
                .graphicsLayer { alpha = logoAlpha }
        ) {
            Box(
                modifier = Modifier.size(layout.heroImageSize + spacing.large + spacing.medium),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    LightGold.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = stringResource(id = R.string.cd_logo),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
            Text(
                text = stringResource(id = R.string.khanabook).uppercase(),
                color = TextLight.copy(alpha = textAlpha),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = "Restaurant POS & Billing",
                color = TextLight.copy(alpha = textAlpha * 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "PIQUANT CONSULTANCY SERVICES",
            color = TextLight.copy(alpha = footerAlpha * 0.4f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = spacing.large)
        )
    }
}
