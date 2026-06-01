@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncState
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncViewModel

private enum class SyncStepState { COMPLETED, IN_PROGRESS, PENDING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialSyncScreen(
    onSyncCompleteNavigateToMain: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    viewModel: InitialSyncViewModel = hiltViewModel()
) {
    val syncState by viewModel.syncState.collectAsState()
    val spacing = KhanaBookTheme.spacing

    LaunchedEffect(syncState) {
        when (syncState) {
            is InitialSyncState.Success -> onSyncCompleteNavigateToMain()
            is InitialSyncState.SessionExpired -> onNavigateToLogin()
            else -> {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "KhanaBook",
                            modifier = Modifier.size(24.dp),
                            tint = KbBrandSaffron
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "KhanaBook",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    if (syncState is InitialSyncState.Syncing || syncState is InitialSyncState.Idle) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Syncing",
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(syncRotation),
                            tint = KbBrandSaffron
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = MaterialTheme.kbTextPrimary
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = syncState) {
                is InitialSyncState.Syncing, InitialSyncState.Idle -> {
                    SyncContent(syncRotation, spacing)
                }
                is InitialSyncState.SessionExpired -> {
                    SessionExpiredContent(onNavigateToLogin, spacing)
                }
                is InitialSyncState.Error -> {
                    ErrorContent(state.message, { viewModel.startInitialSync() }, spacing)
                }
                is InitialSyncState.Success -> {
                    SuccessContent(spacing)
                }
            }
        }
    }
}

@Composable
private fun SyncContent(syncRotation: Float, spacing: Spacing) {
    val progress by animateFloatAsState(
        targetValue = 0.75f,
        animationSpec = tween(1500, easing = LinearEasing),
        label = "syncProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(spacing.huge))

        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeWidth = 10.dp.toPx()
                drawArc(
                    color = KbGray200,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = KbBrandSaffron,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KbBrandSaffron
                )
            )
        }

        Spacer(modifier = Modifier.height(spacing.large))

        Text(
            "Setting up your restaurant...",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.kbTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(spacing.extraLarge))

        SyncStepItem("Downloading menu", SyncStepState.COMPLETED, syncRotation)
        Spacer(modifier = Modifier.height(spacing.medium))
        SyncStepItem("Downloading orders", SyncStepState.COMPLETED, syncRotation)
        Spacer(modifier = Modifier.height(spacing.medium))
        SyncStepItem("Configuring payments", SyncStepState.IN_PROGRESS, syncRotation)
        Spacer(modifier = Modifier.height(spacing.medium))
        SyncStepItem("Ready to serve", SyncStepState.PENDING, syncRotation)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Syncing payment configuration...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.kbTextTertiary,
            modifier = Modifier.padding(bottom = spacing.extraLarge)
        )
    }
}

@Composable
private fun SyncStepItem(text: String, state: SyncStepState, syncRotation: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            SyncStepState.COMPLETED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KbSuccess),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            SyncStepState.IN_PROGRESS -> {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "In Progress",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(syncRotation),
                    tint = KbBrandSaffron
                )
            }
            SyncStepState.PENDING -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(2.dp, KbGray300, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = when (state) {
                SyncStepState.COMPLETED -> MaterialTheme.kbTextPrimary
                SyncStepState.IN_PROGRESS -> KbBrandSaffron
                SyncStepState.PENDING -> MaterialTheme.kbTextTertiary
            }
        )
    }
}

@Composable
private fun SessionExpiredContent(onNavigateToLogin: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Session Expired",
            tint = KbError,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(spacing.large))
        Text(
            "Session expired. Please login again.",
            color = KbError,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(spacing.extraLarge))
        Button(
            onClick = { onNavigateToLogin() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                "Login Again",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Sync Error",
            tint = KbError,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(spacing.large))
        Text(
            message,
            color = KbError,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(spacing.extraLarge))
        Button(
            onClick = { onRetry() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                "Retry",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun SuccessContent(spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.anim_success)
        )
        val lottieProgress by animateLottieCompositionAsState(
            composition = composition,
            iterations = 1
        )

        LottieAnimation(
            composition = composition,
            progress = { lottieProgress },
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(spacing.medium))

        Text(
            "Setup Complete!",
            color = KbSuccess,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}
