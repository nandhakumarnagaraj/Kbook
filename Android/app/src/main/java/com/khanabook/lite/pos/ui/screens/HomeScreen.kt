@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.HomeViewModel
import com.khanabook.lite.pos.ui.designsystem.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun HomeScreen(
    onNewBill: () -> Unit,
    onSearchBill: () -> Unit,
    onOrderStatus: () -> Unit,
    onCallCustomer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel = hiltViewModel()
) {
    val stats by viewModel.todayStats.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val greeting = viewModel.greeting
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val isWideScreen = !layout.isCompact

    val statsReady by viewModel.statsReady.collectAsState()

    var headerVisible by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var primaryVisible by remember { mutableStateOf(false) }
    var actionsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
        delay(80)
        statsVisible = true
        delay(80)
        primaryVisible = true
        delay(80)
        actionsVisible = true
    }

    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.medium)
        ) {

            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = greeting,
                                color = TextGold,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = shopName,
                                color = PrimaryGold,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                    }
                }
            }

            AnimatedVisibility(visible = statsVisible, enter = enterSpec, exit = exitSpec) {
                if (!statsReady) {
                    // Skeleton placeholder while stats load
                    SkeletonCard(modifier = Modifier.fillMaxWidth())
                } else {
                    KhanaBookCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.mediumLarge)
                        ) {
                            Text(
                                text = "Today's Summary",
                                color = PrimaryGold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(spacing.small))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatItem("Orders", stats.orderCount.toString(), Modifier.weight(1f))
                                StatItem("Revenue", CurrencyUtils.formatPrice(stats.revenue), Modifier.weight(1f))
                                StatItem("Customers", stats.customerCount.toString(), Modifier.weight(1f))
                            }
                            if (stats.orderCount > 0 || stats.kdsPendingCount > 0) {
                                Spacer(modifier = Modifier.height(spacing.small))
                                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(spacing.small))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StatItem(
                                        "Avg Order",
                                        CurrencyUtils.formatPrice(stats.avgOrderValue),
                                        Modifier.weight(1f)
                                    )
                                    StatItem(
                                        "Cancelled",
                                        stats.cancelledCount.toString(),
                                        Modifier.weight(1f)
                                    )
                                    StatItem(
                                        "KDS Pending",
                                        stats.kdsPendingCount.toString(),
                                        Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            AnimatedVisibility(visible = primaryVisible, enter = enterSpec, exit = exitSpec) {
                KhanaBookCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    onClick = { onNewBill() },
                    colors = CardDefaults.cardColors(containerColor = PrimaryGold),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Create New Bill",
                                color = DarkBrown1,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Start taking orders",
                                color = DarkBrown2,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = spacing.extraSmall)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = DarkBrown1,
                            modifier = Modifier.size(spacing.huge)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            AnimatedVisibility(visible = actionsVisible, enter = enterSpec, exit = exitSpec) {
            if (isWideScreen) {
                // Adaptive grid for tablets/landscape
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    HomeActionCard(
                        text = "Find Bill",
                        icon = Icons.Default.Search,
                        backgroundColor = CardBG,
                        modifier = Modifier.weight(1f),
                        onClick = onSearchBill
                    )
                    HomeActionCard(
                        text = "Order Status",
                        icon = Icons.Default.Info,
                        backgroundColor = CardBG,
                        modifier = Modifier.weight(1f),
                        onClick = onOrderStatus
                    )
                }
                Spacer(modifier = Modifier.height(spacing.medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    HomeActionCard(
                        text = "Call Customers",
                        icon = Icons.Default.Call,
                        backgroundColor = CardBG,
                        modifier = Modifier.weight(1f),
                        onClick = onCallCustomer
                    )
                    Spacer(modifier = Modifier.weight(1f)) // Empty space for balance
                }
            } else {
                // Vertical list for phones
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                ) {
                    HomeActionCard(
                        text = "Find Bill",
                        icon = Icons.Default.Search,
                        backgroundColor = CardBG,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSearchBill
                    )

                    HomeActionCard(
                        text = "Order Status",
                        icon = Icons.Default.Info,
                        backgroundColor = CardBG,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOrderStatus
                    )

                    HomeActionCard(
                        text = "Call Customers",
                        icon = Icons.Default.Call,
                        backgroundColor = CardBG,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onCallCustomer
                    )
                }
            }
            } // end AnimatedVisibility(actionsVisible)

            Spacer(modifier = Modifier.height(spacing.medium))
        }
    }
}

@Composable
fun SyncStatusHeader(
    connectionStatus: com.khanabook.lite.pos.domain.util.ConnectionStatus,
    unsyncedCount: Int,
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
) {
    val isOnline = connectionStatus == com.khanabook.lite.pos.domain.util.ConnectionStatus.Available
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val currentUser by authViewModel.currentUser.collectAsState()
    val isSessionValid = currentUser != null
    val shouldShowSync = isOnline && isSessionValid && unsyncedCount > 0

    // ── Animate ONLY when there is actually something pending to sync ──────────
    // Previously, rememberInfiniteTransition() was always composed, running
    // rotation + pulse on every frame even when fully synced — wasting battery.
    // Now both values are static 0f when idle.
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
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Subtle glow effect behind the pill when syncing
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
                contentDescription = null,
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
            
            // Animated countdown text
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
fun HomeActionCard(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    KhanaBookCard(
        modifier = modifier
            .height(70.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                    modifier = Modifier.size(iconSize.large)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    text = text,
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(iconSize.medium)
            )
        }
    }
}
