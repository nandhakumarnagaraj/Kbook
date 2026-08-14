@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.model.OrderPaymentFlowMode
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.HomeViewModel
import com.khanabook.lite.pos.ui.viewmodel.NotificationViewModel
import com.khanabook.lite.pos.ui.designsystem.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*

@Composable
fun HomeScreen(
    onNewBill: () -> Unit,
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
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel = hiltViewModel()
) {
    val stats by viewModel.todayStats.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()
    val pendingOnlinePayments by viewModel.pendingOnlinePayments.collectAsStateWithLifecycle()
    val activeDraftBills by viewModel.activeDraftBills.collectAsStateWithLifecycle()
    val quarantinedSyncCount by viewModel.quarantinedSyncCount.collectAsStateWithLifecycle()
    val shopName by viewModel.shopName.collectAsStateWithLifecycle()
    val orderPaymentFlowMode by viewModel.orderPaymentFlowMode.collectAsStateWithLifecycle()
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
        viewModel.message.collect { event ->
            KhanaToast.show(event.message, event.kind)
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
                .widthIn(max = layout.maxContentWidth)
                .align(Alignment.TopCenter)
                .padding(
                    horizontal = layout.contentPadding,
                    vertical = if (layout.compactHomeHeight && layout.isLandscape) spacing.extraSmall else sectionSpacing
                ),
            // Distribute leftover height across section gaps — never stretches cards —
            // but cap each gap so tall windows get rhythm, not 170dp voids.
            verticalArrangement = remember(sectionSpacing, layout.maxSectionGap) {
                BoundedVerticalSpaceBetween(sectionSpacing, layout.maxSectionGap)
            }
        ) {
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (layout.compactHomeHeight) spacing.small else spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        Box(modifier = Modifier.widthIn(max = 160.dp)) {
                            SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                        }
                        NotificationBellIcon(
                            unreadCount = unreadNotificationCount,
                            onClick = onOpenNotifications
                        )
                    }
                }
            }

            if (!layout.compactHomeHeight) {
                AnimatedVisibility(visible = statsVisible, enter = enterSpec, exit = exitSpec) {
                    if (!statsReady) {
                        // Skeleton placeholder while stats load
                        SkeletonCard(modifier = Modifier.fillMaxWidth())
                    } else {
                        KhanaBookCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = layout.cardPaddingHorizontal,
                                    vertical = layout.cardPaddingVertical
                                )
                            ) {
                                Text(
                                    text = "Today's Summary",
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(spacing.extraSmall))
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
                Column(verticalArrangement = Arrangement.spacedBy(sectionSpacing)) {
                    // Warning cards: on compact-height windows show a collapsed chip to
                    // preserve the height budget for all 5 actions. Tap expands details.
                    val hasPaymentWarning = pendingOnlinePayments.isNotEmpty()
                    val hasSyncWarning = quarantinedSyncCount > 0
                    val warningCount = (if (hasPaymentWarning) 1 else 0) + (if (hasSyncWarning) 1 else 0)
                    var warningsExpanded by remember { mutableStateOf(false) }
                    val showFullWarnings = !layout.compactHomeHeight || warningsExpanded

                    if (warningCount > 0 && !showFullWarnings) {
                        // Compact collapsed chip
                        Surface(
                            onClick = { warningsExpanded = true },
                            color = WarningYellow.copy(alpha = 0.14f),
                            shape = KhanaRadii.pill,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = spacing.medium,
                                    vertical = spacing.small
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningYellow,
                                    modifier = Modifier.size(KhanaBookTheme.iconSize.small)
                                )
                                Text(
                                    text = if (warningCount == 1 && hasPaymentWarning) "Unresolved payment"
                                        else if (warningCount == 1) "Sync issue"
                                        else "$warningCount alerts",
                                    color = WarningYellow,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Tap to expand",
                                    color = TextGold,
                                    style = MaterialTheme.typography.labelSmall
                                )
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = WarningYellow,
                                        modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Unresolved Payment",
                                            color = WarningYellow,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Order ${pendingPayment.dailyOrderDisplay} • ${CurrencyUtils.formatPrice(pendingPayment.totalAmount)}",
                                            color = TextLight,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = if (pendingOnlinePayments.size > 1) {
                                        "${pendingOnlinePayments.size} pending payment attempts need review before retrying UPI."
                                    } else {
                                        "Resume or cancel this payment attempt before starting another UPI payment."
                                    },
                                    color = TextGold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                KhanaButtonRow {
                                    KhanaPrimaryButton(
                                        text = "Resume",
                                        onClick = onResumePendingPayment,
                                        modifier = Modifier.weight(1f)
                                    )
                                    KhanaDestructiveButton(
                                        text = "Cancel",
                                        onClick = { viewModel.cancelPendingOnlinePayment(pendingPayment.id) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    if (showFullWarnings && hasSyncWarning) {
                        KhanaBookCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PrimaryGold.copy(alpha = 0.12f)),
                            shape = KhanaRadii.lg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(spacing.medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SyncProblem,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$quarantinedSyncCount quarantined child row(s) need review",
                                        color = TextLight,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Open Sync Center to inspect quarantined bill items and payments.",
                                        color = TextGold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(onClick = onOpenSyncCenter) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                    val primaryActionLabel = if (orderPaymentFlowMode == OrderPaymentFlowMode.PAY_AFTER_FOOD) {
                        "Create New Order"
                    } else {
                        "Create New Bill"
                    }
                    KhanaBookCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNewBill() },
                        colors = CardDefaults.cardColors(containerColor = PrimaryGold),
                        shape = KhanaRadii.xl
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = layout.cardPaddingHorizontal,
                                    vertical = if (layout.compactHomeHeight) spacing.smallMedium else layout.primaryCardVertical
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(layout.primaryIconContainerSize)
                                    .background(DarkBrown1, shape = RoundedCornerShape(50)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = PrimaryGold,
                                    modifier = Modifier.size(layout.primaryIconSize)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primaryActionLabel,
                                    color = DarkBrown1,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                if (!(layout.compactHomeHeight && layout.isLandscape)) {
                                    Text(
                                        text = "Works offline. Sync runs in background.",
                                        color = DarkBrown1.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(top = spacing.extraSmall)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = DarkBrown1,
                                modifier = Modifier.size(layout.primaryIconSize)
                            )
                        }
                    }
                    // Actions live as separate top-level children so BoundedVerticalSpaceBetween
                    // distributes remaining height evenly across ALL section gaps.
                    AnimatedVisibility(visible = actionsVisible, enter = enterSpec, exit = exitSpec) {
                        val activeSubtitle = when {
                            activeDraftBills.isEmpty() -> "No active orders"
                            else -> {
                                val dineIn = activeDraftBills.count { it.orderType == "dine_in" }
                                val takeaway = activeDraftBills.count { it.orderType == "takeaway" }
                                buildString {
                                    append("${activeDraftBills.size} order${if (activeDraftBills.size > 1) "s" else ""} waiting")
                                    val parts = mutableListOf<String>()
                                    if (dineIn > 0) parts.add("$dineIn Dine-in")
                                    if (takeaway > 0) parts.add("$takeaway Takeaway")
                                    if (parts.isNotEmpty()) append(" • ${parts.joinToString(" • ")}")
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            HomeActionCard(
                                text = "Active Orders",
                                subtitle = activeSubtitle,
                                icon = Icons.Default.ShoppingCart,
                                backgroundColor = CardBG,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (activeDraftBills.isEmpty()) {
                                        coroutineScope.launch {
                                            KhanaToast.show("No active order", ToastKind.Info)
                                        }
                                    } else {
                                        onActiveOrder()
                                    }
                                }
                            )
                            HomeActionCard(
                                text = "Find Bill",
                                subtitle = "Search previous invoices",
                                icon = Icons.Default.Search,
                                backgroundColor = CardBG,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onSearchBill
                            )
                            HomeActionCard(
                                text = "Reprint KOT",
                                subtitle = "Kitchen ticket",
                                icon = Icons.Default.Restaurant,
                                backgroundColor = CardBG,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onReprintKds
                            )
                            HomeActionCard(
                                text = "Call Customer",
                                subtitle = "Dial from saved customers",
                                icon = Icons.Default.Call,
                                backgroundColor = CardBG,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onCallCustomer
                            )
                        }
                    } // end AnimatedVisibility(actionsVisible)
                }
            }
        }
    }

}


