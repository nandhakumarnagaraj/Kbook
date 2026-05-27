@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.khanabook.lite.pos.R


@Composable
fun HomeScreen(
    onNewBill: () -> Unit,
    onSearchBill: () -> Unit,
    onReprintKds: () -> Unit,
    onOrderStatus: () -> Unit,
    onCallCustomer: () -> Unit,
    onMarketplaceOrders: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel = hiltViewModel()
) {
    val stats by viewModel.todayStats.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val greeting = viewModel.greeting
    val marketplacePendingCount by viewModel.marketplacePendingCount.collectAsState()
    val complianceAlerts by viewModel.complianceAlerts.collectAsState()
    val dismissedAlerts = remember { mutableListOf<String>() }
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val isWideScreen = !layout.isCompact

    val statsReady by viewModel.statsReady.collectAsState()

    val newBillInteractionSource = remember { MutableInteractionSource() }
    val isNewBillPressed by newBillInteractionSource.collectIsPressedAsState()
    val newBillScale by animateFloatAsState(
        targetValue = if (isNewBillPressed) 0.96f else 1f,
        label = "new_bill_scale"
    )

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

    val enterSpec = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    )
    val exitSpec = fadeOut(spring(stiffness = Spring.StiffnessMediumLow))

    val bgModifier = if (globalIsDark) {
        Modifier.background(KbMidnightBase)
    } else {
        Modifier.background(MaterialTheme.kbBgGradient)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(bgModifier)
    ) {
        // Stitch "Midnight Harvest" mesh gradient accent layers
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KbMeshGradientSaffron)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KbMeshGradientAmber)
        )
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
                        // Brand logo
                        Image(
                            painter = painterResource(id = R.drawable.ic_khanabook_logo),
                            contentDescription = "KhanaBook",
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greeting,
                                color = MaterialTheme.kbTextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = shopName,
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(modifier = Modifier.widthIn(max = 160.dp)) {
                            SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                        }
                    }

                    // Compliance warning banners — shown only when un-dismissed alerts exist
                    val visibleAlerts = complianceAlerts.filter { it.label !in dismissedAlerts }
                    if (visibleAlerts.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            visibleAlerts.forEach { alert ->
                                ComplianceBanner(
                                    alert = alert,
                                    onDismiss = { dismissedAlerts.add(alert.label) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = statsVisible, enter = enterSpec, exit = exitSpec) {
                if (!statsReady) {
                    SkeletonCard(modifier = Modifier.fillMaxWidth())
                } else {
                    // Stitch Glassmorphism stat card with inner border
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, KbGlassBorder, RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = KbGlassSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(spacing.medium)
                            ) {
                                Text(
                                    text = "Today's Summary",
                                    color = MaterialTheme.kbTertiary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
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
            }

            AnimatedVisibility(visible = primaryVisible, enter = enterSpec, exit = exitSpec) {
                Box(contentAlignment = Alignment.Center) {
                    // Stitch "Saffron Glow Zone" — radial mesh gradient behind primary CTA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(130.dp)
                            .background(KbSaffronGlowStrong)
                    )
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(newBillScale)
                            .border(
                                width = 2.dp,
                                color = KbBrandSaffronLight.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(KbMeshHeroGradient)
                                .clickable(
                                    interactionSource = newBillInteractionSource,
                                    indication = ripple(bounded = true, color = Color.White),
                                    onClick = { onNewBill() }
                                )
                                .padding(spacing.large)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Create New Bill",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Start taking orders",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(top = spacing.extraSmall)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "Create New Bill",
                                    tint = Color.White,
                                    modifier = Modifier.size(spacing.huge)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = primaryVisible, enter = enterSpec, exit = exitSpec) {
                // Online Orders card with pulsing Saffron glow
                val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.05f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                Box {
                    if (marketplacePendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(KbBrandSaffron.copy(alpha = pulseAlpha), Color.Transparent)
                                    )
                                )
                        )
                    }
                    
                    HomeActionCard(
                        text = "Online Orders",
                        icon = Icons.Default.ShoppingCart,
                        backgroundColor = CardBG,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onMarketplaceOrders,
                        badgeCount = marketplacePendingCount
                    )
                }
            }


            AnimatedVisibility(visible = actionsVisible, enter = enterSpec, exit = exitSpec) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                    ) {
                        HomeActionGridCard(
                            text = "Find Bill",
                            icon = Icons.Default.Search,
                            backgroundColor = CardBG,
                            modifier = Modifier.weight(1f),
                            onClick = onSearchBill
                        )
                        HomeActionGridCard(
                            text = "Reprint KDS",
                            icon = Icons.Default.Restaurant,
                            backgroundColor = CardBG,
                            modifier = Modifier.weight(1f),
                            onClick = onReprintKds
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                    ) {
                        HomeActionGridCard(
                            text = "Order Status",
                            icon = Icons.Default.Info,
                            backgroundColor = CardBG,
                            modifier = Modifier.weight(1f),
                            onClick = onOrderStatus
                        )
                        HomeActionGridCard(
                            text = "Call Customers",
                            icon = Icons.Default.Call,
                            backgroundColor = CardBG,
                            modifier = Modifier.weight(1f),
                            onClick = onCallCustomer
                        )
                    }
                }
            }

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
        unsyncedCount > 0 -> MaterialTheme.kbSecondary   // amber for syncing
        else -> MaterialTheme.kbTertiary                  // blue for synced
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
    onClick: () -> Unit,
    badgeCount: Int = 0
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
                    tint = MaterialTheme.kbSecondary,
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
                    if (badgeCount > 0) {
                        Text(
                            text = "$badgeCount pending",
                            color = WarningYellow,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(end = spacing.small)
                            .size(28.dp)
                            .background(DangerRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.kbSecondary,
                    modifier = Modifier.size(iconSize.small)
                )
            }
        }
    }
}

@Composable
fun HomeActionGridCard(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    KhanaBookCard(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.kbSecondary,
                    modifier = Modifier.size(iconSize.large)
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = text,
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(DangerRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// ─── Compliance Warning Banner ─────────────────────────────────────────────

@Composable
fun ComplianceBanner(
    alert: HomeViewModel.ComplianceAlert,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val bannerBg: Color
    val textColor: Color
    val alertIcon: ImageVector
    val contentDesc: String
    val status: String
    when (alert.urgency) {
        HomeViewModel.ComplianceAlert.Urgency.EXPIRED  -> {
            bannerBg    = DangerRed.copy(alpha = 0.18f)
            textColor   = DangerRed
            alertIcon   = Icons.Default.GppBad
            contentDesc = "Expired"
            status      = "EXPIRED"
        }
        HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> {
            bannerBg    = DangerRed.copy(alpha = 0.12f)
            textColor   = DangerRed
            alertIcon   = Icons.Default.Warning
            contentDesc = "Critical warning"
            status      = "${alert.daysLeft}d left"
        }
        HomeViewModel.ComplianceAlert.Urgency.HIGH     -> {
            bannerBg    = WarningYellow.copy(alpha = 0.12f)
            textColor   = WarningYellow
            alertIcon   = Icons.Default.WarningAmber
            contentDesc = "High priority warning"
            status      = "${alert.daysLeft}d left"
        }
        HomeViewModel.ComplianceAlert.Urgency.MEDIUM   -> {
            bannerBg    = MaterialTheme.kbSecondary.copy(alpha = 0.10f)
            textColor   = MaterialTheme.kbSecondary
            alertIcon   = Icons.Default.Info
            contentDesc = "Info"
            status      = "${alert.daysLeft}d left"
        }
    }

    val message = when (alert.urgency) {
        HomeViewModel.ComplianceAlert.Urgency.EXPIRED  -> "${alert.label} has expired! Renew immediately."
        HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> "${alert.label} expires in ${abs(alert.daysLeft)} day(s). Renew now!"
        HomeViewModel.ComplianceAlert.Urgency.HIGH     -> "${alert.label} expires in ${alert.daysLeft} days. Renew soon."
        HomeViewModel.ComplianceAlert.Urgency.MEDIUM   -> "${alert.label} expires in ${alert.daysLeft} days."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = bannerBg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(
                imageVector = alertIcon,
                contentDescription = contentDesc,
                tint = textColor,
                modifier = Modifier.size(iconSize.small)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = textColor.copy(alpha = 0.18f)
            ) {
                Text(
                    text = status,
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss alert",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(iconSize.small)
                    )
                }
            }
        }
    }
}
