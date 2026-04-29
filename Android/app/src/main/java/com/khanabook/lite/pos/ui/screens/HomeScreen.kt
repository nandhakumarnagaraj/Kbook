@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
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

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            KhanaToast.show(message, ToastKind.Info)
        }
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
                .statusBarsPadding()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // Equal flex space at the top — pairs with the bottom flex spacer
            // so the content block stays vertically centred and the top/bottom
            // breathing room is the same on tall screens.
            Spacer(modifier = Modifier.weight(1f, fill = true))

            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greeting,
                                color = TextGold,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = shopName,
                                color = PrimaryGold,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(modifier = Modifier.widthIn(max = 160.dp)) {
                            SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                        }
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
                            modifier = Modifier.padding(spacing.medium)
                        ) {
                            Text(
                                text = "Today's Summary",
                                color = PrimaryGold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(spacing.small))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                verticalArrangement = Arrangement.spacedBy(spacing.small),
                                maxItemsInEachRow = 3
                            ) {
                                val statMod = Modifier.weight(1f)
                                StatItem("Orders", stats.orderCount.toString(), statMod)
                                StatItem("Revenue", CurrencyUtils.formatPriceCompact(stats.revenue), statMod)
                                StatItem("Customers", stats.customerCount.toString(), statMod)
                                if (stats.orderCount > 0 || stats.kdsPendingCount > 0) {
                                    StatItem("Avg Order", CurrencyUtils.formatPriceCompact(stats.avgOrderValue), statMod)
                                    StatItem("Cancelled", stats.cancelledCount.toString(), statMod)
                                    StatItem("KDS Pending", stats.kdsPendingCount.toString(), statMod)
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = primaryVisible, enter = enterSpec, exit = exitSpec) {
                KhanaBookCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNewBill() },
                    colors = CardDefaults.cardColors(containerColor = PrimaryGold),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
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
                                color = DarkBrown1.copy(alpha = 0.85f),
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

            AnimatedVisibility(visible = actionsVisible, enter = enterSpec, exit = exitSpec) {
            if (isWideScreen) {
                // Single row of 3 actions on tablets — no empty filler.
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
                        text = "Reprint KDS",
                        icon = Icons.Default.Restaurant,
                        backgroundColor = CardBG,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.reprintPendingKds() }
                    )
                    HomeActionCard(
                        text = "Order Status",
                        icon = Icons.Default.Info,
                        backgroundColor = CardBG,
                        modifier = Modifier.weight(1f),
                        onClick = onOrderStatus
                    )
                    HomeActionCard(
                        text = "Call Customers",
                        icon = Icons.Default.Call,
                        backgroundColor = CardBG,
                        modifier = Modifier.weight(1f),
                        onClick = onCallCustomer
                    )
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
                        text = "Reprint KDS",
                        icon = Icons.Default.Restaurant,
                        backgroundColor = CardBG,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.reprintPendingKds() }
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

            // Mirror of the top flex spacer — keeps top and bottom whitespace
            // equal so the content block looks vertically centred.
            Spacer(modifier = Modifier.weight(1f, fill = true))
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
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
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
                Text(
                    text = text,
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
