@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.feature.reports.ui
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.domain.model.OrderType
import com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.feature.reports.viewmodel.HomeViewModel
import com.khanabook.lite.pos.feature.notifications.viewmodel.NotificationViewModel
import com.khanabook.lite.pos.core.designsystem.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*

@Composable
fun HomeScreen(
    onNewBill: (Boolean?) -> Unit,
    onActiveOrder: () -> Unit,
    onOpenActiveOrder: (Long) -> Unit = {},
    onResumePendingPayment: () -> Unit,
    onOpenSyncCenter: () -> Unit,
    onOpenPrinterSettings: () -> Unit,
    onSearchBill: () -> Unit,
    onReprintKds: () -> Unit,
    onCallCustomer: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel = hiltViewModel()
) {
    val summaryScope by viewModel.summaryScope.collectAsStateWithLifecycle()
    val stats by viewModel.todayStats.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()
    val pendingOnlinePayments by viewModel.pendingOnlinePayments.collectAsStateWithLifecycle()
    val activeDraftBills by viewModel.activeDraftBills.collectAsStateWithLifecycle()
    val quarantinedSyncCount by viewModel.quarantinedSyncCount.collectAsStateWithLifecycle()
    val clockDriftWarning by viewModel.clockDriftWarning.collectAsStateWithLifecycle()
    val shopName by viewModel.shopName.collectAsStateWithLifecycle()
    val orderPaymentFlowMode by viewModel.orderPaymentFlowMode.collectAsStateWithLifecycle()
    val quickModeEnabled by viewModel.quickModeEnabled.collectAsStateWithLifecycle()
    val quickModeResolved by viewModel.quickModeResolved.collectAsStateWithLifecycle()
    val showActiveOrders = orderPaymentFlowMode == OrderPaymentFlowMode.PAY_AFTER_FOOD
    val greeting = viewModel.greeting
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val isWideScreen = !layout.isCompact

    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val unreadNotificationCount by notificationViewModel.unreadCount.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val statsReady by viewModel.statsReady.collectAsStateWithLifecycle()

    // Responsive rhythm: section spacing grows with available height (8/16/24dp tiers),
    // card interiors and typography follow the resolved window tier.
    val sectionSpacing = layout.sectionSpacing

    // rememberSaveable: entrance flags and the expanded-warnings state must survive
    // configuration changes (rotation) instead of replaying the intro and re-collapsing.
    var headerVisible by rememberSaveable { mutableStateOf(false) }
    var statsVisible by rememberSaveable { mutableStateOf(false) }
    var primaryVisible by rememberSaveable { mutableStateOf(false) }
    var actionsVisible by rememberSaveable { mutableStateOf(false) }

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
        viewModel.message.collect { event ->
            KhanaToast.show(event.message, event.kind)
        }
    }

    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        val isTablet = layout.typeScaleTier == TypeScaleTier.Tablet
        val canFitWithoutScroll = maxHeight >= 640.dp && !layout.isLandscape
        val scrollState = rememberScrollState()

        val primaryActionLabel = when {
            quickModeEnabled -> "Create Quick Bill"
            orderPaymentFlowMode == OrderPaymentFlowMode.PAY_AFTER_FOOD -> "Create New Order"
            else -> "Create Normal Bill"
        }

        val activeSubtitle = when {
            activeDraftBills.isEmpty() -> "No active orders"
            else -> {
                val dineIn = activeDraftBills.count { it.orderType == OrderType.DINE_IN }
                val takeaway = activeDraftBills.count { it.orderType == OrderType.TAKEAWAY }
                buildString {
                    append("${activeDraftBills.size} order${if (activeDraftBills.size > 1) "s" else ""} waiting")
                    val parts = mutableListOf<String>()
                    if (dineIn > 0) parts.add("$dineIn Dine-in")
                    if (takeaway > 0) parts.add("$takeaway Takeaway")
                    if (parts.isNotEmpty()) append("   ${parts.joinToString("   ")}")
                }
            }
        }

        val hasPaymentWarning = pendingOnlinePayments.isNotEmpty()
        val hasSyncWarning = viewModel.isOwner && quarantinedSyncCount > 0
        val warningCount = (if (hasPaymentWarning) 1 else 0) + (if (hasSyncWarning) 1 else 0)
        val hasClockDriftWarning = viewModel.isOwner && clockDriftWarning
        var warningsExpanded by rememberSaveable { mutableStateOf(false) }
        val showFullWarnings = !layout.compactHomeHeight || warningsExpanded

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layout.maxContentWidth)
                .align(Alignment.TopCenter)
                .then(
                    if (canFitWithoutScroll) Modifier
                    else Modifier.verticalScroll(scrollState)
                )
                .padding(horizontal = layout.contentPadding)
                .padding(
                    top = if (layout.compactHomeHeight && layout.isLandscape) spacing.extraSmall else spacing.small,
                    bottom = spacing.smallMedium
                ),
            verticalArrangement = remember(sectionSpacing, isTablet, canFitWithoutScroll) {
                if (isTablet) BoundedVerticalSpaceBetween(sectionSpacing, layout.maxSectionGap)
                else Arrangement.spacedBy(spacing.smallMedium)
            }
        ) {
            // 1. Header
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.hairline)) {
                        if (!layout.compactHomeHeight) {
                            Text(
                                text = greeting,
                                color = TextGold,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = shopName,
                            color = PrimaryGold,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(modifier = Modifier.widthIn(max = (160 * LocalDensity.current.fontScale).dp)) {
                        SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                    }
                    NotificationBellIcon(
                        unreadCount = unreadNotificationCount,
                        onClick = onOpenNotifications
                    )
                }
            }

            // 2. Today's Summary Card (Weight = 1.1f)
            if (!layout.compactHomeHeight) {
                KhanaBookCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (canFitWithoutScroll) Modifier.weight(1.1f) else Modifier)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (canFitWithoutScroll) Modifier.fillMaxHeight() else Modifier)
                            .padding(
                                horizontal = layout.cardPaddingHorizontal,
                                vertical = spacing.smallMedium
                            ),
                        verticalArrangement = if (canFitWithoutScroll) Arrangement.SpaceEvenly else Arrangement.spacedBy(spacing.small)
                    ) {
                        Text(
                            text = "Today's Summary",
                            color = TextGold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.small),
                            maxItemsInEachRow = 3
                        ) {
                            val statMod = Modifier.weight(1f)
                            StatItem("Orders", stats.orderCount.toString(), statMod, large = true)
                            StatItem("Avg Order", CurrencyUtils.formatPriceCompact(stats.avgOrderValue), statMod, large = true)
                            StatItem("KOT Pending", stats.kdsPendingCount.toString(), statMod, large = true)
                        }
                    }
                }
            }

            // Warnings if present
            if (warningCount > 0 || hasClockDriftWarning) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.smallMedium)) {
                    if (warningCount > 0 && !showFullWarnings) {
                        Surface(
                            onClick = { warningsExpanded = true },
                            color = WarningYellow.copy(alpha = 0.14f),
                            shape = KhanaRadii.pill,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Icon(Icons.Default.Warning, null, tint = WarningYellow, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
                                Text(
                                    text = if (warningCount == 1 && hasPaymentWarning) "Unresolved payment"
                                        else if (warningCount == 1) "Sync issue"
                                        else "$warningCount alerts",
                                    color = WarningYellow,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("Tap to expand", color = TextGold, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (showFullWarnings && hasPaymentWarning) {
                        val pendingPayment = pendingOnlinePayments.first()
                        KhanaBookCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = WarningYellow.copy(alpha = 0.14f)),
                            shape = KhanaRadii.lg
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                ) {
                                    Icon(Icons.Default.Warning, null, tint = WarningYellow, modifier = Modifier.size(KhanaBookTheme.iconSize.medium))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Unresolved Payment", color = WarningYellow, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Order ${pendingPayment.dailyOrderDisplay} • ${CurrencyUtils.formatPrice(pendingPayment.totalAmount)}", color = TextLight, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                    KhanaPrimaryButton("Resume", onClick = onResumePendingPayment, modifier = Modifier.weight(1f))
                                    KhanaDestructiveButton("Cancel", onClick = { viewModel.cancelPendingOnlinePayment(pendingPayment.id) }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (viewModel.isOwner && showFullWarnings && hasSyncWarning) {
                        KhanaBookCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PrimaryGold.copy(alpha = 0.12f)),
                            shape = KhanaRadii.lg
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Icon(Icons.Default.SyncProblem, null, tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (quarantinedSyncCount == 1) "$quarantinedSyncCount item needs review before it can sync" else "$quarantinedSyncCount items need review before they can sync",
                                        color = TextLight, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                                    )
                                    Text("Open Sync Center to check and fix these items.", color = TextGold, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = onOpenSyncCenter) { Text("Open") }
                            }
                        }
                    }
                    if (viewModel.isOwner && clockDriftWarning) {
                        KhanaBookCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = WarningYellow.copy(alpha = 0.12f)),
                            shape = KhanaRadii.lg
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Icon(Icons.Default.Schedule, null, tint = WarningYellow, modifier = Modifier.size(KhanaBookTheme.iconSize.medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Device Clock Time Drift Detected", color = WarningYellow, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("This tablet's clock is off by more than 3 minutes. Turn on 'Set time automatically' in Android Settings so bills and invoices stay in order.", color = TextLight, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Billing Mode Toggle (Weight = 0.55f)
            BillingModeToggle(
                isQuickMode = quickModeEnabled,
                onModeSelected = { isQuick -> viewModel.setQuickMode(isQuick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canFitWithoutScroll) Modifier.weight(0.55f) else Modifier)
            )

            // 4. Hero Primary CTA Card (Weight = 1.35f)
            KhanaBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (canFitWithoutScroll) Modifier.weight(1.35f) else Modifier),
                onClick = { onNewBill(quickModeResolved) },
                colors = CardDefaults.cardColors(containerColor = PrimaryGold),
                shape = KhanaRadii.xl
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (canFitWithoutScroll) Modifier.fillMaxHeight() else Modifier)
                        .padding(
                            horizontal = layout.cardPaddingHorizontal,
                            vertical = if (canFitWithoutScroll) spacing.smallMedium else layout.primaryCardVertical
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (canFitWithoutScroll) 56.dp else layout.primaryIconContainerSize)
                            .background(DarkBrown1, shape = RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(if (canFitWithoutScroll) 30.dp else layout.primaryIconSize)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = primaryActionLabel,
                            color = DarkBrown1,
                            style = if (canFitWithoutScroll) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!(layout.compactHomeHeight && layout.isLandscape)) {
                            Text(
                                text = if (viewModel.isOwner) "Works offline. Sync runs in background." else "Start taking orders right away.",
                                color = DarkBrown1.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = spacing.extraSmall)
                            )
                        }
                    }
                }
            }

            // 5. Action Cards (Each Weight = 0.85f)
            if (canFitWithoutScroll && layout.homeActionColumns == 1) {
                if (showActiveOrders) {
                    HomeActionCard(
                        text = "Active Orders",
                        subtitle = activeSubtitle,
                        icon = Icons.Default.ShoppingCart,
                        backgroundColor = CardBG,
                        modifier = Modifier.fillMaxWidth().weight(0.85f),
                        onClick = onActiveOrder
                    )
                }
                HomeActionCard(
                    text = "Find Bill",
                    subtitle = "Search previous invoices",
                    icon = Icons.Default.Search,
                    backgroundColor = CardBG,
                    modifier = Modifier.fillMaxWidth().weight(0.85f),
                    onClick = onSearchBill
                )
                HomeActionCard(
                    text = "Reprint KOT",
                    subtitle = "Kitchen Order Ticket",
                    icon = Icons.Default.Restaurant,
                    backgroundColor = CardBG,
                    modifier = Modifier.fillMaxWidth().weight(0.85f),
                    onClick = onReprintKds
                )
                if (!quickModeEnabled) {
                    HomeActionCard(
                        text = "Call Customer",
                        subtitle = "Dial from saved customers",
                        icon = Icons.Default.Call,
                        backgroundColor = CardBG,
                        modifier = Modifier.fillMaxWidth().weight(0.85f),
                        onClick = onCallCustomer
                    )
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = layout.homeActionColumns,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    val actionModifier = if (layout.homeActionColumns > 1) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    if (showActiveOrders) {
                        HomeActionCard(
                            text = "Active Orders",
                            subtitle = activeSubtitle,
                            icon = Icons.Default.ShoppingCart,
                            backgroundColor = CardBG,
                            modifier = actionModifier,
                            onClick = onActiveOrder
                        )
                    }
                    HomeActionCard(
                        text = "Find Bill",
                        subtitle = "Search previous invoices",
                        icon = Icons.Default.Search,
                        backgroundColor = CardBG,
                        modifier = actionModifier,
                        onClick = onSearchBill
                    )
                    HomeActionCard(
                        text = "Reprint KOT",
                        subtitle = "Kitchen Order Ticket",
                        icon = Icons.Default.Restaurant,
                        backgroundColor = CardBG,
                        modifier = actionModifier,
                        onClick = onReprintKds
                    )
                    if (!quickModeEnabled) {
                        HomeActionCard(
                            text = "Call Customer",
                            subtitle = "Dial from saved customers",
                            icon = Icons.Default.Call,
                            backgroundColor = CardBG,
                            modifier = actionModifier,
                            onClick = onCallCustomer
                        )
                    }
                }
            }
        }
    }
}
