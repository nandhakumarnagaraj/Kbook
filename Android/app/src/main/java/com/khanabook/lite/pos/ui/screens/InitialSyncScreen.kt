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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncState
import com.khanabook.lite.pos.ui.viewmodel.InitialSyncViewModel

private enum class SyncStepState { COMPLETED, IN_PROGRESS, PENDING }

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

    // Midnight purple gradient background — matches Login/SignUp/Splash
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.kbHeaderGradient
            )
    ) {
        when (val state = syncState) {
            is InitialSyncState.Syncing, InitialSyncState.Idle -> SyncContentPurple(syncRotation, spacing)
            is InitialSyncState.SessionExpired -> SessionExpiredContentPurple(onNavigateToLogin, spacing)
            is InitialSyncState.Error -> ErrorContentPurple(state.message, { viewModel.startInitialSync() }, spacing)
            is InitialSyncState.Success -> SuccessContentPurple(spacing)
        }
    }
}

@Composable
private fun SyncContentPurple(syncRotation: Float, spacing: Spacing) {
    val progress by animateFloatAsState(
        targetValue = 0.75f,
        animationSpec = tween(1500, easing = LinearEasing),
        label = "syncProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(spacing.huge))

        // Circular progress arc — saffron on dark
        val trackColor = Color(0xFF1A1230)
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeWidth = 10.dp.toPx()
                drawArc(
                    color = trackColor,
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
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(spacing.extraLarge))

        SyncStepItemPurple("Downloading menu", SyncStepState.COMPLETED, syncRotation)
        Spacer(modifier = Modifier.height(spacing.medium))
        SyncStepItemPurple("Downloading orders", SyncStepState.COMPLETED, syncRotation)
        Spacer(modifier = Modifier.height(spacing.medium))
        SyncStepItemPurple("Configuring payments", SyncStepState.IN_PROGRESS, syncRotation)
        Spacer(modifier = Modifier.height(spacing.medium))
        SyncStepItemPurple("Ready to serve", SyncStepState.PENDING, syncRotation)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Syncing payment configuration...",
            style = MaterialTheme.typography.bodySmall,
            color = KbLavender.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = spacing.extraLarge)
        )
    }
}

@Composable
private fun SyncStepItemPurple(text: String, state: SyncStepState, syncRotation: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            SyncStepState.COMPLETED -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(KbWhatsAppGreen),
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
                        .border(2.dp, MaterialTheme.kbOutlineSubtle, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = when (state) {
                SyncStepState.COMPLETED -> Color.White
                SyncStepState.IN_PROGRESS -> KbBrandSaffron
                SyncStepState.PENDING -> KbLavender.copy(alpha = 0.6f)
            }
        )
    }
}

@Composable
private fun SessionExpiredContentPurple(onNavigateToLogin: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
            colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
            shape = RoundedCornerShape(16.dp),
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
private fun ErrorContentPurple(message: String, onRetry: () -> Unit, spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
            colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
            shape = RoundedCornerShape(16.dp),
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
private fun SuccessContentPurple(spacing: Spacing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(KbWhatsAppGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Success",
                tint = KbWhatsAppGreen,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        Text(
            "Setup Complete!",
            color = KbWhatsAppGreen,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            "Your restaurant is ready to serve", color = KbLavender,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
