@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun InitialSyncScreen(
    onSyncCompleteNavigateToMain: () -> Unit,
    viewModel: InitialSyncViewModel = hiltViewModel()
) {
    val syncState by viewModel.syncState.collectAsState()
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    LaunchedEffect(syncState) {
        if (syncState is InitialSyncState.Success) {
            onSyncCompleteNavigateToMain()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = syncState) {
                is InitialSyncState.Syncing, InitialSyncState.Idle -> {
                    val composition by rememberLottieComposition(
                        LottieCompositionSpec.RawRes(R.raw.anim_sync)
                    )
                    val lottieProgress by animateLottieCompositionAsState(
                        composition = composition,
                        iterations = LottieConstants.IterateForever
                    )

                    LottieAnimation(
                        composition = composition,
                        progress = { lottieProgress },
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.height(spacing.large))

                    Text(
                        "Setting up your workspace...",
                        color = TextLight,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Please wait. Do not close the app.",
                        color = TextGold.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = spacing.small)
                    )

                    Spacer(modifier = Modifier.height(spacing.large))

                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(4.dp),
                        color = PrimaryGold,
                        trackColor = PrimaryGold.copy(alpha = 0.2f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                is InitialSyncState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sync Error",
                        tint = ErrorPink,
                        modifier = Modifier.size(iconSize.xxlarge)
                    )
                    Spacer(modifier = Modifier.height(spacing.large))
                    Text(
                        text = state.message,
                        color = ErrorPink,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(spacing.extraLarge))
                    Button(
                        onClick = { viewModel.startInitialSync() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            "Retry",
                            color = DarkBrown1,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                is InitialSyncState.Success -> {
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
                        color = SuccessGreen,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
