@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.KhanaBookPurpleBackground
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    onNavigateToInitialSync: () -> Unit,
    onNavigateToAppLock: () -> Unit = {},
    viewModel: SplashViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLowEnd = remember(context) { KbMotion.isLowEndDevice(context) }

    var logoVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var subtitleVisible by remember { mutableStateOf(false) }
    var spinnerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        logoVisible = true
        delay(150)
        titleVisible = true
        delay(150)
        subtitleVisible = true
        delay(150)
        spinnerVisible = true
    }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 650, easing = KbMotion.EasingStandard),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "logoAlpha"
    )
    val logoRotation by animateFloatAsState(
        targetValue = if (logoVisible) 0f else -4f,
        animationSpec = tween(durationMillis = 650, easing = KbMotion.EasingStandard),
        label = "logoRotation"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "titleAlpha"
    )
    val titleSpacing by animateFloatAsState(
        targetValue = if (titleVisible) 0.08f else -0.05f,
        animationSpec = tween(durationMillis = 650, easing = KbMotion.EasingStandard),
        label = "titleSpacing"
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (subtitleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "subtitleAlpha"
    )
    val subtitleTranslationY by animateFloatAsState(
        targetValue = if (subtitleVisible) 0f else 15f,
        animationSpec = tween(durationMillis = 500, easing = KbMotion.EasingStandard),
        label = "subtitleTranslationY"
    )

    val spinnerAlpha by animateFloatAsState(
        targetValue = if (spinnerVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "spinnerAlpha"
    )

    AnimatedMidnightBackground(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        isLowEnd = isLowEnd
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                        rotationZ = logoRotation
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background mesh glow directly behind logo (skipped if low-end device)
                if (!isLowEnd) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF8B5CF6).copy(alpha = 0.45f),
                                            Color(0xFFD946EF).copy(alpha = 0.15f),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = size.width / 2
                                    )
                                )
                            }
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.ic_khanabook_logo),
                    contentDescription = stringResource(id = R.string.cd_logo),
                    modifier = Modifier.size(88.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = stringResource(id = R.string.khanabook),
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    letterSpacing = titleSpacing.em
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Restaurant POS & Management",
                color = Color(0xFF7C6FCD),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = subtitleAlpha
                    translationY = subtitleTranslationY
                }
            )
        }

        // Saffron spinner at bottom
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .graphicsLayer {
                    alpha = spinnerAlpha
                },
            color = KbBrandSaffron,
            strokeWidth = 3.dp
        )
    }

    LaunchedEffect(state) {
        when (state) {
            is SplashViewModel.SplashState.NavigateToLogin -> onNavigateToLogin()
            is SplashViewModel.SplashState.NavigateToMain -> onNavigateToMain()
            is SplashViewModel.SplashState.NavigateToInitialSync -> onNavigateToInitialSync()
            is SplashViewModel.SplashState.NavigateToAppLock -> onNavigateToAppLock()
            else -> {}
        }
    }
}

@Composable
fun AnimatedMidnightBackground(
    modifier: Modifier = Modifier,
    isLowEnd: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    if (isLowEnd) {
        // Fallback: static gradient for performance optimization on low-end terminals
        Box(
            modifier = modifier.background(KbMidnightGradient),
            contentAlignment = Alignment.Center,
            content = content
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "ambientMotion")

    // Slow loops (12 to 15 seconds)
    val animProgressX by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progressX"
    )
    val animProgressY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progressY"
    )

    Box(
        modifier = modifier
            .background(KbMidnightGradient)
            .drawBehind {
                val width = size.width
                val height = size.height

                // Top-right blob moves slowly
                val trRadius = width * 0.7f
                val trX = width * (0.85f + 0.15f * animProgressX)
                val trY = height * (0.05f + 0.1f * animProgressY)
                val trCenter = Offset(trX, trY)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            KbBrandViolet.copy(alpha = 0.32f),
                            Color(0xFF4C1D95).copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = trCenter,
                        radius = trRadius
                    ),
                    center = trCenter,
                    radius = trRadius
                )

                // Bottom-left blob moves slowly
                val blRadius = width * 0.6f
                val blX = width * (0.05f - 0.1f * animProgressY)
                val blY = height * (0.95f - 0.15f * animProgressX)
                val blCenter = Offset(blX, blY)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF9D174D).copy(alpha = 0.22f),
                            Color(0xFF831843).copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = blCenter,
                        radius = blRadius
                    ),
                    center = blCenter,
                    radius = blRadius
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}
