@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*

@Composable
internal fun SyncStatusHeader(
    connectionStatus: com.khanabook.lite.pos.domain.util.ConnectionStatus,
    unsyncedCount: Int,
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
) {
    val isOnline = connectionStatus == com.khanabook.lite.pos.domain.util.ConnectionStatus.Available
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isSessionValid = currentUser != null
    val shouldShowSync = isOnline && isSessionValid && unsyncedCount > 0

    val rotation: Float
    val pulseAlpha: Float
    if (shouldShowSync) {
        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
        rotation = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        ).value
        pulseAlpha = infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        ).value
    } else {
        rotation = 0f
        pulseAlpha = 0.15f
    }

    val containerColor = when {
        !isOnline -> DangerRed
        !isSessionValid -> WarningYellow
        unsyncedCount > 0 -> PrimaryGold
        else -> SuccessGreen
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(KhanaRadii.xl)
    ) {
        if (unsyncedCount > 0 && isOnline && isSessionValid) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(8.dp)
                    .background(containerColor.copy(alpha = pulseAlpha))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(containerColor.copy(alpha = if (unsyncedCount > 0) pulseAlpha else 0.15f))
                .padding(horizontal = spacing.smallMedium, vertical = spacing.small - spacing.hairline)
        ) {
            Icon(
                imageVector = when {
                    !isOnline -> Icons.Default.CloudOff
                    !isSessionValid -> Icons.Default.Lock
                    unsyncedCount > 0 -> Icons.Default.Sync
                    else -> Icons.Default.CloudDone
                },
                contentDescription = when {
                    !isOnline -> "Offline - no internet connection"
                    !isSessionValid -> "Session expired - login required"
                    unsyncedCount > 0 -> "$unsyncedCount items pending sync"
                    else -> "All data synced to cloud"
                },
                tint = containerColor,
                modifier = Modifier
                    .size(iconSize.small)
                    .then(
                        if (shouldShowSync)
                            Modifier.rotate(rotation)
                        else Modifier
                    )
            )
            
            Spacer(modifier = Modifier.width(spacing.small))
            
            AnimatedContent(
                targetState = when {
                    !isOnline -> "Offline"
                    !isSessionValid -> "Auth Required"
                    unsyncedCount > 0 -> "$unsyncedCount"
                    else -> "Cloud Synced"
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "sync_text"
            ) { targetText ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (unsyncedCount > 0 && isOnline && isSessionValid) {
                        Text(
                            text = "Syncing... ",
                            color = TextLight,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = targetText,
                        color = if (unsyncedCount > 0 || !isOnline || !isSessionValid) containerColor else TextLight,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            shadow = if (unsyncedCount > 0) androidx.compose.ui.graphics.Shadow(
                                color = containerColor.copy(alpha = 0.5f),
                                blurRadius = 4f
                            ) else null
                        )
                    )
                }
            }
        }
    }
}


@Composable
internal fun HomeActionCard(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    KhanaBookCard(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = KhanaRadii.lg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.size(iconSize.medium)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Column {
                    Text(
                        text = text,
                        color = TextLight,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(iconSize.small)
            )
        }
    }
}
