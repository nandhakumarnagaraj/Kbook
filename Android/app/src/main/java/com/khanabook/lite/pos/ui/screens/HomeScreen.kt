@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.HomeViewModel
import com.khanabook.lite.pos.ui.viewmodel.HomeViewModel.HomeStats
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.NotificationViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import com.khanabook.lite.pos.R
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun HomeScreen(
    onNewBill: () -> Unit,
    onSearchBill: () -> Unit,
    onReprintKds: () -> Unit,
    onOrderStatus: () -> Unit,
    onCallCustomer: () -> Unit,
    onMarketplaceOrders: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onViewReports: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    authViewModel: com.khanabook.lite.pos.ui.viewmodel.AuthViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel()
) {
    val pageBg = KbMidnightBase
    val spacing = KhanaBookTheme.spacing
    val layoutDensity = KhanaBookTheme.density
    val haptic = LocalHapticFeedback.current

    val stats by viewModel.todayStats.collectAsState()
    val recentBills by viewModel.recentBills.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val shopName by viewModel.shopName.collectAsState()
    val logoModel by viewModel.logoModel.collectAsState()
    val greeting = viewModel.greeting
    val marketplacePendingCount by viewModel.marketplacePendingCount.collectAsState()
    val complianceAlerts by viewModel.complianceAlerts.collectAsState()
    val dismissedAlerts = remember { mutableStateListOf<String>() }
    val statsReady by viewModel.statsReady.collectAsState()
    val pushNotifications by notificationViewModel.notifications.collectAsState()
    val pushUnreadCount by notificationViewModel.unreadCount.collectAsState()
    
    val notificationCount = remember(
        unsyncedCount,
        marketplacePendingCount,
        complianceAlerts,
        stats,
        pushUnreadCount
    ) {
        val syncIssues = if (unsyncedCount > 0) 1 else 0
        val marketAlerts = if (marketplacePendingCount > 0) 1 else 0
        val complianceCount = complianceAlerts.size
        val kdsAlerts = if (stats.kdsPendingCount > 0) 1 else 0
        pushUnreadCount + syncIssues + marketAlerts + complianceCount + kdsAlerts
    }

    var headerVisible by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var actionsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        headerVisible = true
        delay(80)
        statsVisible = true
        delay(80)
        actionsVisible = true
    }

    LaunchedEffect(Unit) {
        viewModel.message.collect { message ->
            KhanaToast.show(message, ToastKind.Info)
        }
    }

    val enterSpec: EnterTransition = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
        slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 }
    val exitSpec: ExitTransition = fadeOut(spring(stiffness = Spring.StiffnessMediumLow))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.kbHeaderGradient)
                    ) {
                        Column(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    modifier = Modifier.size(56.dp),
                                    shape = KbShape.Medium,
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!logoModel.isNullOrBlank()) {
                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                    .data(logoModel)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Shop Logo",
                                                modifier = Modifier.fillMaxSize().clip(KbShape.Medium),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_khanabook_logo),
                                                contentDescription = "Logo",
                                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = greeting,
                                        color = KbAccentOrangeLight,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = shopName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SyncStatusHeader(connectionStatus, unsyncedCount, authViewModel)
                                    
                                    NotificationBellIcon(
                                        unreadCount = notificationCount,
                                        onClick = {
                                            notificationViewModel.refreshFromServer()
                                            onNotifications()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    val visibleAlerts = complianceAlerts.filter { it.label !in dismissedAlerts }
                    if (visibleAlerts.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(spacing.small),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
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

            AnimatedVisibility(
                visible = statsVisible && connectionStatus == com.khanabook.lite.pos.domain.util.ConnectionStatus.Unavailable,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(300)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(200))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    color = KbWarning.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "You're offline — bills will sync when reconnected",
                            color = KbWarning,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Restaurant status banner
            RestaurantStatusBanner(stats = stats, lastOrderTime = stats.lastOrderTime)

            // Today's Summary section
            AnimatedVisibility(visible = statsVisible, enter = enterSpec, exit = exitSpec) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Today's Summary",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        )
                        Text(
                            text = "View All",
                            color = KbBrandSaffron,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clip(KbShape.Small)
                                .clickable { onViewReports() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // 4 equal stat tiles: Total Orders · Revenue · Avg Ticket · Cancelled
                    val avgTicket = if (stats.orderCount > 0) stats.revenue / stats.orderCount else 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCardSmall(
                            value = stats.orderCount.toString(),
                            label = "Total Orders",
                            icon = Icons.Default.Receipt,
                            modifier = Modifier.weight(1f),
                            accentColor = KbAccentBlueBright
                        )
                        MetricCardSmall(
                            value = CurrencyUtils.formatPrice(stats.revenue),
                            label = "Revenue",
                            icon = Icons.Default.Payments,
                            modifier = Modifier.weight(1f),
                            accentColor = KbSuccess
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCardSmall(
                            value = CurrencyUtils.formatPrice(avgTicket),
                            label = "Avg Ticket",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            modifier = Modifier.weight(1f),
                            accentColor = KbAccentPurple
                        )
                        MetricCardSmall(
                            value = stats.cancelledCount.toString(),
                            label = "Cancelled",
                            icon = Icons.Default.Cancel,
                            modifier = Modifier.weight(1f),
                            accentColor = if (stats.cancelledCount > 0) KbError else MaterialTheme.kbTextSecondary
                        )
                    }
                }
            }

            // Actions & Utilities Section
            AnimatedVisibility(visible = actionsVisible, enter = enterSpec, exit = exitSpec) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Primary CTA: "+ New Bill"
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNewBill()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .kbPressScale()
                            .shadow(4.dp, KbShape.Medium),
                        shape = KbShape.Medium,
                        colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CREATE NEW BILL", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    // 2. Secondary action grid (mockup set)
                    Text(
                        text = "Quick Actions",
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryActionChip(
                            label = "Orders",
                            icon = Icons.AutoMirrored.Filled.List,
                            onClick = onSearchBill,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryActionChip(
                            label = "Order Status",
                            icon = Icons.Default.HourglassEmpty,
                            onClick = onOrderStatus,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryActionChip(
                            label = "Find Bill",
                            icon = Icons.Default.Search,
                            onClick = onSearchBill,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryActionChip(
                            label = "Reprint Bill",
                            icon = Icons.Default.Print,
                            onClick = onReprintKds,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryActionChip(
                            label = "Call Customers",
                            icon = Icons.Default.People,
                            onClick = onCallCustomer,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryActionChip(
                            label = "Menu",
                            icon = Icons.Default.Restaurant,
                            onClick = onMenuClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Recent Activity Section
            AnimatedVisibility(visible = statsVisible, enter = enterSpec, exit = exitSpec) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        )
                        if (recentBills.isNotEmpty()) {
                            Text(
                                text = "View All",
                                color = KbBrandSaffron,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.clickable { onSearchBill() }
                            )
                        }
                    }

                    if (recentBills.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.kbOutlineSubtle, KbShape.Medium),
                            shape = KbShape.Medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No recent orders today",
                                    color = MaterialTheme.kbTextTertiary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recentBills.forEach { bill ->
                                RecentActivityItem(
                                    bill = bill,
                                    onClick = onSearchBill
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * The four secondary actions (Find Bill, Reprint KDS, Order Status, Call Customers).
 *
 * Layout follows the user's Layout Density setting (Settings → Display):
 *  - "horizontal" → each card on its own full-width line, stacked vertically
 *  - otherwise     → the standard 2×2 grid
 */
@Composable
private fun HomeSecondaryActions(
    density: String,
    onSearchBill: () -> Unit,
    onReprintKds: () -> Unit,
    onOrderStatus: () -> Unit,
    onCallCustomer: () -> Unit
) {
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> HomeActionGridCard("Find Bill", Icons.Default.Search, KbAccentVioletBorder, KbAccentViolet, KbAccentVioletSurface, m, onSearchBill) },
        { m -> HomeActionGridCard("Reprint KDS", Icons.Default.Print, KbAccentEmeraldBorder, KbAccentEmerald, KbAccentEmeraldSurface, m, onReprintKds) },
        { m -> HomeActionGridCard("Order Status", Icons.Default.AccessTime, KbAccentBlueBorder, KbAccentBlueBright, KbAccentBlueSurface, m, onOrderStatus) },
        { m -> HomeActionGridCard("Call Customers", Icons.Default.Call, KbAccentRedBorder, KbAccentRed, KbAccentRedSurface, m, onCallCustomer) }
    )

    if (density == "horizontal") {
        // One card per line, full width.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEach { card -> card(Modifier.fillMaxWidth()) }
        }
    } else {
        // Standard 2×2 grid.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.chunked(2).forEach { rowCards ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowCards.forEach { card -> card(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MetricCardSmall(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.kbTextPrimary
) {
    Card(
        modifier = modifier
            .border(1.dp, MaterialTheme.kbOutlineSubtle, KbShape.Medium)
            .shadow(1.dp, KbShape.Medium),
        shape = KbShape.Medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.kbTextPrimary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.kbTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SecondaryActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .kbPressScale(),
        shape = KbShape.Medium,
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.kbTextPrimary,
            containerColor = MaterialTheme.kbBgCard
        ),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = KbBrandSaffron)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentActivityItem(
    bill: com.khanabook.lite.pos.data.local.entity.BillEntity,
    onClick: () -> Unit
) {
    val timeText = remember(bill.createdAt) {
        val diff = System.currentTimeMillis() - bill.createdAt
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> {
                val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                sdf.format(java.util.Date(bill.createdAt))
            }
        }
    }

    val statusText = when (bill.orderStatus) {
        "cancelled" -> "Cancelled"
        else -> if (bill.paymentStatus == "paid" || bill.paymentStatus == "success") "Paid" else "Unpaid"
    }

    val statusColor = when (bill.orderStatus) {
        "cancelled" -> KbError
        else -> if (bill.paymentStatus == "paid" || bill.paymentStatus == "success") KbSuccess else KbWarning
    }

    val icon = when (bill.paymentMode.lowercase()) {
        "cash" -> Icons.Default.AttachMoney
        "upi", "qr" -> Icons.Default.QrCode
        else -> Icons.Default.CreditCard
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.kbOutlineSubtle, KbShape.Medium),
        shape = KbShape.Medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(statusColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bill #${bill.dailyOrderDisplay}",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "$statusText • $timeText",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "₹${bill.totalAmount}",
                color = if (bill.orderStatus == "cancelled") KbError else MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                )
            )
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    accentColor: Color,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = KbShape.Large,
                clip = false
            ),
        shape = KbShape.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accentColor)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = value,
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1
            )
            if (badge != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = badge,
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun TodaySummaryContent(
    stats: HomeStats,
    textPrimaryColor: Color,
    textMutedColor: Color
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn(
            value = stats.orderCount.toString(),
            label = "Orders",
            subtitle = "today",
            valueColor = textPrimaryColor,
            labelColor = textMutedColor,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(MaterialTheme.kbOutlineSubtle)
        )
        StatColumn(
            value = CurrencyUtils.formatPriceCompact(stats.revenue),
            label = "Revenue",
            subtitle = "today",
            valueColor = textPrimaryColor,
            labelColor = textMutedColor,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(MaterialTheme.kbOutlineSubtle)
        )
        StatColumn(
            value = stats.customerCount.toString(),
            label = "Customers",
            subtitle = "today",
            valueColor = textPrimaryColor,
            labelColor = textMutedColor,
            modifier = Modifier.weight(1f)
        )
    }

}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    subtitle: String?,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = labelColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TodaySummaryEmpty(onNewBill: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(spacing.small))
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            tint = MaterialTheme.kbTextTertiary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(spacing.small))
        Text(
            "No orders yet today",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.kbTextSecondary
        )
        Text(
            "Start your first bill to see today's summary",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.kbTextTertiary
        )
        Spacer(Modifier.height(spacing.medium))
        PrimaryButton(
            text = "Create New Bill",
            onClick = onNewBill,
            modifier = Modifier,
            leadingIcon = Icons.Default.Add
        )
        Spacer(Modifier.height(spacing.small))
    }
}

@Composable
fun SummaryStatItem(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
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

    val rotation: Float
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
    } else {
        rotation = 0f
    }

    val (textColor, borderColor) = when {
        !isOnline -> Pair(KbError, KbError)
        !isSessionValid -> Pair(KbWarning, KbWarning)
        unsyncedCount > 0 -> Pair(KbBrandSaffron, KbBrandSaffron)
        else -> Pair(KbAccentEmerald, KbAccentEmerald)
    }
    val bgColor = KbVioletBadgeSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
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
                tint = textColor,
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
                Text(
                    text = targetText,
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun HomeActionCard(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val spacing = KhanaBookTheme.spacing
    val cardBg = MaterialTheme.kbBgCard
    val cardBorderColor = MaterialTheme.kbOutlineSubtle
    val accentColor = KbSuccess

    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            })
            .border(1.dp, cardBorderColor, KbShape.Large),
        shape = KbShape.Large,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Column {
                    Text(
                        text = text,
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeCount > 0) {
                        Text(
                            text = "$badgeCount pending",
                            color = KbWarning,
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
                            .background(KbError, CircleShape),
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
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun HomeActionGridCard(
    text: String,
    icon: ImageVector,
    borderColor: Color,
    iconColor: Color,
    iconBgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badgeCount: Int = 0
) {
    val spacing = KhanaBookTheme.spacing
    val cardBg = MaterialTheme.kbBgCard

    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            })
            .border(1.5.dp, borderColor, KbShape.Large)
            .shadow(
                elevation = 2.dp,
                shape = KbShape.Large,
                clip = false
            ),
        shape = KbShape.Large,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(spacing.smallMedium))
            Text(
                text = text,
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RestaurantStatusBanner(
    stats: HomeStats,
    lastOrderTime: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.kbOutlineSubtle, KbShape.Large)
            .shadow(2.dp, KbShape.Large, clip = false),
        shape = KbShape.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .background(KbSuccess.copy(alpha = 0.16f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(KbSuccess, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Restaurant is Open",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                val orderSuffix = if (stats.orderCount == 1) "order" else "orders"
                val ordersText = "${stats.orderCount} $orderSuffix placed today"
                val lastTimeText = lastOrderTime?.let { " • Last at $it" } ?: ""
                Text(
                    text = "$ordersText$lastTimeText",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.kbTextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ComplianceBanner(
    alert: HomeViewModel.ComplianceAlert,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val result = when (alert.urgency) {
        HomeViewModel.ComplianceAlert.Urgency.EXPIRED  -> Triple(KbRedSubtle, KbError, Icons.Default.GppBad) to ("Expired" to "EXPIRED")
        HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> Triple(KbRedSubtle, KbError, Icons.Default.Warning) to ("Critical warning" to "${alert.daysLeft}d left")
        HomeViewModel.ComplianceAlert.Urgency.HIGH     -> Triple(KbWarning.copy(alpha = KbOpacity.StatusBg), KbWarning, Icons.Default.WarningAmber) to ("High priority warning" to "${alert.daysLeft}d left")
        HomeViewModel.ComplianceAlert.Urgency.MEDIUM   -> Triple(KbSuccess.copy(alpha = KbOpacity.StatusBg), KbSuccess, Icons.Default.Info) to ("Info" to "${alert.daysLeft}d left")
    }
    val bannerBg = result.first.first
    val textColor = result.first.second
    val alertIcon = result.first.third
    val contentDesc = result.second.first
    val status = result.second.second

    val message = when (alert.urgency) {
        HomeViewModel.ComplianceAlert.Urgency.EXPIRED  -> "${alert.label} has expired! Renew immediately."
        HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> "${alert.label} expires in ${abs(alert.daysLeft)} day(s). Renew now!"
        HomeViewModel.ComplianceAlert.Urgency.HIGH     -> "${alert.label} expires in ${alert.daysLeft} days. Renew soon."
        HomeViewModel.ComplianceAlert.Urgency.MEDIUM   -> "${alert.label} expires in ${alert.daysLeft} days."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KbShape.Small,
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

@Composable
private fun OperationalPulseStrip(
    unsyncedCount: Int,
    marketplacePendingCount: Int,
    complianceCount: Int,
    isOnline: Boolean
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PulseTile(
            modifier = Modifier.weight(1f),
            title = if (isOnline) "Sync" else "Offline",
            value = if (isOnline) "Live" else "Queued",
            tone = if (isOnline) KbSuccess else KbWarning
        )
        PulseTile(
            modifier = Modifier.weight(1f),
            title = "Unsynced",
            value = unsyncedCount.toString(),
            tone = if (unsyncedCount > 0) KbWarning else MaterialTheme.kbSecondary
        )
        PulseTile(
            modifier = Modifier.weight(1f),
            title = "Marketplace",
            value = marketplacePendingCount.toString(),
            tone = if (marketplacePendingCount > 0) KbBrandSaffron else MaterialTheme.kbSecondary
        )
        PulseTile(
            modifier = Modifier.weight(1f),
            title = "Alerts",
            value = complianceCount.toString(),
            tone = if (complianceCount > 0) KbError else KbSuccess
        )
    }
    Spacer(modifier = Modifier.height(spacing.extraSmall))
}

@Composable
private fun PulseTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    tone: Color
) {
    KhanaBookCard(
        modifier = modifier,
        shape = KbShape.Medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = value,
                color = tone,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
internal fun NotificationCenterSheet(
    unsyncedCount: Int,
    marketplacePendingCount: Int,
    kdsPendingCount: Int,
    complianceAlerts: List<HomeViewModel.ComplianceAlert>,
    isOnline: Boolean,
    pushNotifications: List<com.khanabook.lite.pos.data.local.entity.NotificationEntity> = emptyList(),
    pushUnreadCount: Int = 0,
    onNotificationClick: (com.khanabook.lite.pos.data.local.entity.NotificationEntity) -> Unit = {},
    onMarkAllNotificationsRead: () -> Unit = {},
    onRefreshNotifications: () -> Unit = {},
    onMarketplaceOrders: () -> Unit,
    onReprintKds: () -> Unit,
    onOrderStatus: () -> Unit,
    onNewBill: () -> Unit,
    onDismiss: () -> Unit,
    showHeader: Boolean = true
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Operations Center",
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Real-time alerts, sync queues, and restaurant actions",
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pushUnreadCount > 0) {
                        TextButton(onClick = onMarkAllNotificationsRead) {
                            Text("Mark all read", color = KbAccentPurple, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = MaterialTheme.kbPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Sync & Connection Status (Sync Status)
        if (!isOnline || unsyncedCount > 0) {
            val statusColor = if (!isOnline) KbError else KbWarning
            Card(
                modifier = Modifier.fillMaxWidth().kbPressScale(),
                shape = KbShape.Medium,
                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(statusColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (!isOnline) Icons.Default.CloudOff else Icons.Default.Sync,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (!isOnline) "Network Connection Offline" else "Unsynced Transactions Enqueued",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusColor
                        )
                        Text(
                            text = if (!isOnline) "Your transactions are safely saved locally and will sync when network returns."
                            else "$unsyncedCount receipt(s) waiting to sync to the cloud database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.kbTextSecondary
                        )
                    }
                }
            }
        }

        // 2. Critical Compliance Warnings (Critical Alerts & Compliance Reminders)
        if (complianceAlerts.isNotEmpty()) {
            Text(
                text = "Compliance Alerts",
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            complianceAlerts.forEach { alert ->
                val alertColor = when (alert.urgency) {
                    HomeViewModel.ComplianceAlert.Urgency.EXPIRED,
                    HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> KbError
                    HomeViewModel.ComplianceAlert.Urgency.HIGH -> KbWarning
                    HomeViewModel.ComplianceAlert.Urgency.MEDIUM -> KbSuccess
                }
                Card(
                    modifier = Modifier.fillMaxWidth().kbPressScale(),
                    shape = KbShape.Medium,
                    colors = CardDefaults.cardColors(containerColor = alertColor.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(alertColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = alertColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alert.label,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = alertColor
                            )
                            Text(
                                text = when (alert.urgency) {
                                    HomeViewModel.ComplianceAlert.Urgency.EXPIRED -> "License expired! Renew immediately to maintain operations."
                                    HomeViewModel.ComplianceAlert.Urgency.CRITICAL -> "Urgent! Expires in ${kotlin.math.abs(alert.daysLeft)} day(s)."
                                    else -> "Renewal required in ${alert.daysLeft} days."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.kbTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. Online Orders & Kitchen Prints (Pending Store Actions)
        if (marketplacePendingCount > 0 || kdsPendingCount > 0) {
            Text(
                text = "Pending Store Actions",
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            if (marketplacePendingCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().kbPressScale(),
                    shape = KbShape.Medium,
                    colors = CardDefaults.cardColors(containerColor = KbBrandSaffron.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(KbBrandSaffron.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = KbBrandSaffron,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "New Online Orders",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = KbBrandSaffron
                            )
                            Text(
                                text = "$marketplacePendingCount marketplace order(s) awaiting acceptance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.kbTextSecondary
                            )
                        }
                        Button(
                            onClick = onMarketplaceOrders,
                            colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                            shape = KbShape.Small,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (kdsPendingCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().kbPressScale(),
                    shape = KbShape.Medium,
                    colors = CardDefaults.cardColors(containerColor = KbSuccess.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(KbSuccess.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = KbSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pending Kitchen Prints",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = KbSuccess
                            )
                            Text(
                                text = "$kdsPendingCount print tickets enqueued in KDS print task manager.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.kbTextSecondary
                            )
                        }
                        Button(
                            onClick = onReprintKds,
                            colors = ButtonDefaults.buttonColors(containerColor = KbSuccess),
                            shape = KbShape.Small,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Activity Timeline (Payment Events, Notification History)
        Text(
            text = "Activity Timeline",
            color = MaterialTheme.kbTextSecondary,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )

        if (pushNotifications.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = KbShape.Medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No operational events logged today.",
                        color = MaterialTheme.kbTextTertiary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pushNotifications.take(6).forEach { notification ->
                    com.khanabook.lite.pos.ui.designsystem.NotificationRow(
                        notification = notification,
                        onClick = { onNotificationClick(notification) }
                    )
                }
                if (pushUnreadCount > 0) {
                    TextButton(
                        onClick = onRefreshNotifications,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Refresh Logs", color = KbAccentPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tone: Color,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val haptic = LocalHapticFeedback.current
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = KbShape.Medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Row(
            modifier = Modifier.padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tone.copy(alpha = 0.12f), KbShape.Small),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.kbTextSecondary
            )
        }
    }
}
