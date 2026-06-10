@file:OptIn(
    ExperimentalMaterial3Api::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.data.remote.dto.MerchantCustomerOrderDetailResponse
import com.khanabook.lite.pos.data.remote.dto.MerchantCustomerOrderSummaryResponse
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.DateUtils
import com.khanabook.lite.pos.domain.util.sendInvoiceViaSms
import com.khanabook.lite.pos.domain.util.shareInvoiceViaWhatsAppLink
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.ReportsViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.ui.viewmodel.StorefrontOrdersViewModel
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import java.text.SimpleDateFormat

@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    storefrontOrdersViewModel: StorefrontOrdersViewModel = hiltViewModel()
) {
    val allRows by viewModel.orderDetailsTable.collectAsState()
    val selectedBillDetails by viewModel.selectedBillDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val onlineOrders by storefrontOrdersViewModel.orders.collectAsState()
    val selectedOnlineOrder by storefrontOrdersViewModel.selectedOrder.collectAsState()
    val onlineLoading by storefrontOrdersViewModel.isLoading.collectAsState()
    val onlineRefreshing by storefrontOrdersViewModel.isRefreshing.collectAsState()
    val onlineUpdatingOrderIds by storefrontOrdersViewModel.updatingOrderIds.collectAsState()
    val onlineError by storefrontOrdersViewModel.error.collectAsState()
    val haptic = LocalHapticFeedback.current
    val spacing = KhanaBookTheme.spacing
    var selectedBillId by remember { mutableStateOf<Long?>(null) }
    var selectedSource by rememberSaveable { mutableStateOf("POS") }
    val enabledModes = remember(profile) {
        profile?.let { com.khanabook.lite.pos.domain.manager.PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    var statusFilter by rememberSaveable { mutableStateOf("All") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredRows = remember(allRows, statusFilter, searchQuery) {
        allRows.filter { row ->
            val matchesStatus = when (statusFilter) {
                "Active" -> row.orderStatus == OrderStatus.DRAFT
                "Completed" -> row.orderStatus == OrderStatus.COMPLETED
                "Cancelled" -> row.orderStatus == OrderStatus.CANCELLED
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                row.dailyNo.contains(searchQuery, ignoreCase = true) ||
                row.lifetimeNo.toString().contains(searchQuery)
            matchesStatus && matchesSearch
        }
    }

    LaunchedEffect(Unit) {
        viewModel.setTimeFilter("Daily")
    }
    LaunchedEffect(selectedSource) {
        if (selectedSource == "ONLINE" && onlineOrders.isEmpty()) {
            storefrontOrdersViewModel.loadOrders()
        }
    }

    var headerVisible by remember { mutableStateOf(false) }
    var bodyVisible by remember { mutableStateOf(false) }
    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))

    LaunchedEffect(Unit) {
        headerVisible = true
        kotlinx.coroutines.delay(80)
        bodyVisible = true
    }

    fun onStatusChange(billId: Long, newStatus: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.updateOrderStatus(billId, newStatus)
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            viewModel.setCustomDateRange(start, end)
                        }
                        showDateRangePicker = false
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                        dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("OK", color = MaterialTheme.kbSecondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel", color = MaterialTheme.kbTextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.kbBgCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .widthIn(max = 900.dp)
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    showModeToggle = false,
                    title = {
                        Text(
                            text = "Select Custom Range",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.medium, bottom = spacing.small),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.kbTertiary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    headline = {
                        Text(
                            text = formatDateRangeHeadline(
                                dateRangePickerState.selectedStartDateMillis,
                                dateRangePickerState.selectedEndDateMillis
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = spacing.medium),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.kbSecondary,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.kbBgCard,
                    titleContentColor = MaterialTheme.kbSecondary,
                    headlineContentColor = MaterialTheme.kbSecondary,
                    weekdayContentColor = MaterialTheme.kbSecondary.copy(alpha = 0.7f),
                    dayContentColor = MaterialTheme.kbTextPrimary,
                    selectedDayContainerColor = KbBrandSaffron,
                    selectedDayContentColor = MaterialTheme.kbBgCard,
                    todayContentColor = MaterialTheme.kbSecondary
                )
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = spacing.small)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.kbHeaderGradient)
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Orders",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            OrderFilterChip(
                                label = "POS Orders",
                                isSelected = selectedSource == "POS",
                                onClick = { selectedSource = "POS" },
                                modifier = Modifier.weight(1f)
                            )
                            OrderFilterChip(
                                label = "Online Orders",
                                isSelected = selectedSource == "ONLINE",
                                onClick = {
                                    val hasOnlineSetup = profile?.let {
                                        it.easebuzzEnabled || it.zomatoEnabled || it.swiggyEnabled || it.ownWebsiteEnabled
                                    } == true
                                    if (hasOnlineSetup) {
                                        selectedSource = "ONLINE"
                                    } else {
                                        scope.launch {
                                            KhanaToast.show("Complete online setup in Payment Configuration first", ToastKind.Warning)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium)
                                .background(Color(0xFF0E0822).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("Daily", "Weekly", "Monthly", "Custom").forEach { title ->
                                    OrderFilterChip(
                                        label = title,
                                        isSelected = timeFilter == title,
                                        onClick = {
                                            if (title == "Custom") {
                                                showDateRangePicker = true
                                            } else {
                                                viewModel.setTimeFilter(title)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val isGstEnabled = profile?.gstEnabled == true

            if (selectedSource == "ONLINE") {
                AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec, modifier = Modifier.weight(1f)) {
                    OnlineOrdersPane(
                        orders = onlineOrders,
                        isLoading = onlineLoading,
                        isRefreshing = onlineRefreshing,
                        error = onlineError,
                        updatingOrderIds = onlineUpdatingOrderIds,
                        onRefresh = { storefrontOrdersViewModel.loadOrders(forceRefresh = true) },
                        onOrderClick = { storefrontOrdersViewModel.loadOrder(it) }
                    )
                }
            } else if (isLoading) {
                AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec, modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium)
                    ) {
                        Spacer(modifier = Modifier.height(spacing.small))
                        repeat(6) {
                            OrderCardSkeleton()
                            Spacer(modifier = Modifier.height(spacing.small))
                        }
                    }
                }
            } else if (allRows.isEmpty()) {
                AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(spacing.small))
                            Text(
                                "No orders in this period",
                                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = spacing.extraSmall)
                        ) {
                            val statuses = listOf("All", "Active", "Completed", "Cancelled")
                            items(statuses) { status ->
                                val isSelected = statusFilter == status
                                Surface(
                                    onClick = { statusFilter = status },
                                    shape = KbShape.ExtraLarge,
                                    color = if (isSelected) KbBrandSaffron
                                            else MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                                    border = if (isSelected) null
                                            else BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle)
                                ) {
                                    Text(
                                        text = status,
                                        color = if (isSelected) Color.White
                                               else MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.small))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium),
                            placeholder = {
                                Text("Search orders...", color = MaterialTheme.kbTextTertiary)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.kbTextTertiary)
                            },
                            singleLine = true,
                            shape = KbShape.Small,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.kbPrimary,
                                unfocusedBorderColor = MaterialTheme.kbOutlineSubtle,
                                focusedTextColor = MaterialTheme.kbTextPrimary,
                                unfocusedTextColor = MaterialTheme.kbTextPrimary,
                                cursorColor = MaterialTheme.kbPrimary,
                                focusedContainerColor = MaterialTheme.kbBgCard,
                                unfocusedContainerColor = MaterialTheme.kbBgCard
                            )
                        )

                        Spacer(modifier = Modifier.height(spacing.small))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.medium,
                                end = spacing.medium,
                                bottom = spacing.bottomListPadding
                            ),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            items(filteredRows, key = { it.billId }) { row ->
                                var showCancelDialog by remember { mutableStateOf(false) }
                                var pendingPartMode by remember { mutableStateOf<PaymentMode?>(null) }

                                OrderCard(
                                    row = row,
                                    onClick = {
                                        selectedBillId = row.billId
                                        viewModel.loadBillDetails(row.billId)
                                    },
                                    onLongClick = {
                                        scope.launch {
                                            viewModel.getOrderDetail(row.billId)?.let { detail ->
                                                shareInvoiceViaWhatsAppLink(context, detail, profile)
                                            }
                                        }
                                    }
                                )

                                if (showCancelDialog) {
                                    CancelOrderDialog(
                                        onDismiss = { showCancelDialog = false },
                                        onConfirm = { reason ->
                                            viewModel.cancelOrder(row.billId, reason)
                                            showCancelDialog = false
                                        }
                                    )
                                }

                                pendingPartMode?.let { mode ->
                                    PartAmountDialog(
                                        mode = mode,
                                        totalAmount = row.salesAmount,
                                        onDismiss = { pendingPartMode = null },
                                        onConfirm = { p1, p2 ->
                                            viewModel.updatePaymentMode(row.billId, mode.dbValue, p1, p2)
                                            pendingPartMode = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedBillId?.let {
            OrderDetailsDialog(
                billWithItems = selectedBillDetails,
                profile = profile,
                onDismiss = {
                    selectedBillId = null
                    viewModel.clearBillDetails()
                }
            )
        }

        selectedOnlineOrder?.let { order ->
            OnlineOrderDetailsDialog(
                order = order,
                isUpdating = onlineUpdatingOrderIds.contains(order.orderId),
                onDismiss = { storefrontOrdersViewModel.clearSelectedOrder() },
                onStatusUpdate = { nextStatus ->
                    storefrontOrdersViewModel.updateOrderStatus(order.orderId, nextStatus)
                }
            )
        }
    }
}

@Composable
private fun OrderCard(
    row: OrderDetailRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val isCancelled = row.orderStatus == OrderStatus.CANCELLED

    // Accent bar color per status — matches screenshot
    val accentColor = when (row.orderStatus) {
        OrderStatus.COMPLETED -> KbSuccess  // green
        OrderStatus.CANCELLED -> KbError  // red
        else                  -> Color(0xFF6366F1)  // indigo/active
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isCancelled) Modifier.graphicsLayer { alpha = KbOpacity.Muted } else Modifier
                )
        ) {
            // Left accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(spacing.medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "#KB-${row.dailyNo}", color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${row.payMode.displayLabel} · INV${row.lifetimeNo}", color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = CurrencyUtils.formatPrice(row.salesAmount),
                        color = KbBrandSaffron,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = timeAgo(row.salesDate),
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OrderStatusChip(row.orderStatus)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCardSkeleton() {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = KbShape.Medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(20.dp)
                        .clip(KbShape.Small)
                        .background(MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f))
                )
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(20.dp)
                        .clip(KbShape.Small)
                        .background(MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f))
                )
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(16.dp)
                        .clip(KbShape.Small)
                        .background(MaterialTheme.kbOutlineSubtle.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(16.dp)
                        .clip(KbShape.Small)
                        .background(MaterialTheme.kbOutlineSubtle.copy(alpha = 0.2f))
                )
            }
            Spacer(modifier = Modifier.height(spacing.small))
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(26.dp)
                    .clip(KbShape.ExtraLarge)
                    .background(MaterialTheme.kbOutlineSubtle.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun OrderStatusChip(status: OrderStatus) {
    if (status == OrderStatus.DRAFT) return

    val (label, color) = when (status) {
        OrderStatus.COMPLETED -> "COMPLETED" to KbSuccess  // green
        OrderStatus.CANCELLED -> "CANCELLED" to KbError  // red
        else                  -> "ACTIVE"    to Color(0xFF6366F1)  // indigo
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
fun PeriodTabs(selectedFilter: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf("Daily", "Weekly", "Monthly", "Custom")
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        tabs.forEach { title ->
            OrderFilterChip(
                label = title,
                isSelected = selectedFilter == title,
                onClick = { onTabSelected(title) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun OrderFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) KbBrandSaffron else Color.Transparent,
        animationSpec = tween(200),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label = "chip_content"
    )
    val borderColor = if (isSelected) KbBrandSaffron else Color.White.copy(alpha = 0.15f)
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, borderColor),
        contentColor = contentColor,
        enabled = enabled
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun TableHeader(isGstEnabled: Boolean) {
    val spacing = KhanaBookTheme.spacing
    val invoiceHeader = if (isGstEnabled) "Tax Inv No" else "Invoice No"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.kbBgCard.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .padding(vertical = spacing.small, horizontal = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Order No", 0.9f)
        HeaderCell(invoiceHeader, 1.7f)
        HeaderCell("Mode", 1.7f)
        HeaderCell("Status", 2.0f)
        HeaderCell("Date", 1.7f)
    }
}

@Composable
fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = MaterialTheme.kbPrimary,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
        textAlign = TextAlign.Center,
        lineHeight = 12.sp
    )
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = MaterialTheme.kbTextPrimary
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = color,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize, fontWeight = fontWeight),
        textAlign = TextAlign.Center,
        lineHeight = 12.sp
    )
}

@Composable
fun OrderTableRow(
    row: OrderDetailRow,
    enabledModes: List<PaymentMode>,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onShareText: () -> Unit,
    onRequestCancel: () -> Unit,
    onStatusChange: (String) -> Unit,
    onPayModeChange: (PaymentMode) -> Unit
) {
    var statusExpanded by remember { mutableStateOf(false) }
    var payModeExpanded by remember { mutableStateOf(false) }
    val spacing = KhanaBookTheme.spacing
    val isCancelled = row.orderStatus == OrderStatus.CANCELLED
    val isTodayBill = remember(row.salesDate) {
        val billDate = java.time.Instant.ofEpochMilli(row.salesDate)
            .atZone(java.time.ZoneId.of("Asia/Kolkata"))
            .toLocalDate()
        billDate == java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
    }
    val canEdit = !isCancelled && isTodayBill

    val stripeColor = if (row.dailyNo.toIntOrNull()?.rem(2) == 0) KbZebraOdd else KbZebraEven
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShareText
            )
            .padding(vertical = spacing.hairline)
            .background(
                if (isCancelled) KbZebraOdd.copy(alpha = 0.25f) else stripeColor,
                RoundedCornerShape(6.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(
                row.dailyNo, 0.9f,
                color = if (isCancelled) MaterialTheme.kbTextPrimary.copy(alpha = 0.35f) else MaterialTheme.kbTextPrimary
            )
            TableCell(
                "INV${row.lifetimeNo}", 1.7f,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) MaterialTheme.kbTextPrimary.copy(alpha = 0.35f) else MaterialTheme.kbTextPrimary
            )

            Box(modifier = Modifier.weight(1.7f), contentAlignment = Alignment.Center) {
                val color = if (!canEdit) Color.Gray else getPayModeColor(row.payMode)
                Surface(
                    onClick = { if (canEdit) payModeExpanded = true },
                    color = color.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(horizontal = spacing.hairline)
                ) {
                    Text(
                        text = row.payMode.displayLabel.uppercase(),
                        color = color,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = payModeExpanded,
                    onDismissRequest = { payModeExpanded = false },
                    modifier = Modifier.background(MaterialTheme.kbBgCard)
                ) {
                    enabledModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayLabel, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onPayModeChange(mode); payModeExpanded = false }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(2.0f), contentAlignment = Alignment.Center) {
                val statusColor = when (row.orderStatus) {
                    OrderStatus.COMPLETED -> KbSuccess
                    OrderStatus.CANCELLED -> KbError
                    else -> MaterialTheme.kbTextSecondary
                }
                Surface(
                    onClick = { if (canEdit) statusExpanded = true },
                    color = statusColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(horizontal = spacing.extraSmall)
                ) {
                    Text(
                        text = when (row.orderStatus) {
                            OrderStatus.COMPLETED -> "COMPLETED"
                            OrderStatus.CANCELLED -> "CANCELLED"
                            else -> "DRAFT"
                        },
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        lineHeight = 10.sp
                    )
                }
                if (canEdit) {
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                        modifier = Modifier.background(MaterialTheme.kbBgCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Completed", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onStatusChange(OrderStatus.COMPLETED.dbValue); statusExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel Order", color = KbError, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)) },
                            onClick = { statusExpanded = false; onRequestCancel() }
                        )
                    }
                }
            }

            TableCell(DateUtils.formatDisplayDate(row.salesDate), 1.7f, fontSize = 9.sp)
        }

        if (isCancelled && row.cancelReason.isNotBlank()) {
            Text(
                text = "Reason: ${row.cancelReason}",
                color = KbError.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = spacing.medium, bottom = spacing.extraSmall)
            )
        }
    }
}

@Composable
fun CancelOrderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val presetReasons = listOf("Wrong order", "Customer left", "Duplicate bill", "Test bill", "Other")
    var selectedReason by remember { mutableStateOf("Customer left") }
    var customReason by remember { mutableStateOf("") }

    KhanaBookSelectionDialog(
        title = "Cancel Order",
        onDismissRequest = onDismiss,
        message = "Select a reason:",
        options = presetReasons.map { reason ->
            SelectionDialogOption(
                value = reason,
                title = reason,
                selectedAccent = KbError,
                onSelect = {
                    selectedReason = reason
                    if (reason != "Other") customReason = ""
                }
            )
        },
        selectedValue = selectedReason,
        trailingContent = {
            if (selectedReason == "Other") {
                KhanaBookInputField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    label = "Other Reason",
                    placeholder = "Describe the reason...",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
        },
        cancelLabel = "Keep Order",
        actions = {
            TextButton(
                onClick = {
                    val finalReason = if (selectedReason == "Other") customReason.trim() else selectedReason
                    if (finalReason.isNotBlank()) onConfirm(finalReason)
                },
                enabled = selectedReason.isNotBlank() && (selectedReason != "Other" || customReason.isNotBlank())
            ) {
                Text("Cancel Order", color = KbError, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    ) {}
}

@Composable
fun PartAmountDialog(
    mode: PaymentMode,
    totalAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val labels = PaymentModeManager.getPartLabels(mode)
    val split = remember(totalAmount) { BillCalculator.splitPartPayment(totalAmount) }
    var p1Text by remember(totalAmount) { mutableStateOf(split.first) }
    var p2Text by remember(totalAmount) { mutableStateOf(split.second) }

    val p1 = p1Text.toDoubleOrNull() ?: 0.0
    val p2 = p2Text.toDoubleOrNull() ?: 0.0
    val isValid = BillCalculator.validatePartPayment(p1Text, p2Text, totalAmount)

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = mode.displayLabel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text(
                    "Total: ${CurrencyUtils.formatPrice(totalAmount)}",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = p1Text,
                        onValueChange = { p1Text = it },
                        label = { Text("${labels.first} Amount", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        isError = !isValid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.kbPrimary,
                            unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.kbTextPrimary,
                            unfocusedTextColor = MaterialTheme.kbTextPrimary
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = p2Text,
                        onValueChange = { p2Text = it },
                        label = { Text("${labels.second} Amount", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        isError = !isValid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.kbPrimary,
                            unfocusedBorderColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.kbTextPrimary,
                            unfocusedTextColor = MaterialTheme.kbTextPrimary
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                }
                if (!isValid) {
                    Text(
                        "Sum must equal ${CurrencyUtils.formatPrice(totalAmount)} (Current: ${CurrencyUtils.formatPrice(p1 + p2)})",
                        color = KbError,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                val total = totalAmount.toDoubleOrNull() ?: 0.0
                val remaining = total - p1 - p2
                Text(
                    text = "Remaining: ₹${CurrencyUtils.formatPrice(remaining)}",
                    color = when {
                        remaining <= 0 -> KbSuccess
                        else -> KbWarning
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = MaterialTheme.kbPrimary, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = { onConfirm(p1Text, p2Text) },
            enabled = isValid
        ) {
            Text("Confirm", color = MaterialTheme.kbPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> KbSuccess
        PaymentMode.UPI -> MaterialTheme.kbSecondary
        PaymentMode.POS -> KbBrandSaffron
        PaymentMode.ZOMATO -> KbSuccess
        PaymentMode.SWIGGY -> SwiggyOrange
        else -> MaterialTheme.kbSecondary
    }
}

private fun periodRange(tab: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    val start: Calendar = when (tab) {
        0 -> {
            (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        1 -> {
            (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -6)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        2 -> {
            (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        else -> cal
    }

    val end: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return start.timeInMillis to end.timeInMillis
}

private fun formatDateRangeHeadline(startMillis: Long?, endMillis: Long?): String {
    val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val startText = startMillis?.let { formatter.format(Date(it)) } ?: "Start date"
    val endText = endMillis?.let { formatter.format(Date(it)) } ?: "End date"
    return "$startText - $endText"
}

private fun timeAgo(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes} min${if (minutes == 1L) "" else "s"} ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMillis))
    }
}

private val COL_ONLINE_ORDER_ID = 1.0f
private val COL_ONLINE_STATUS = 1.2f
private val COL_ONLINE_VIEW = 0.8f
private val COL_ONLINE_ACTION = 1.4f
private val COL_ONLINE_READY = 1.2f

@Composable
private fun OnlineOrdersPane(
    orders: List<MerchantCustomerOrderSummaryResponse>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    error: String?,
    updatingOrderIds: Set<Long>,
    onRefresh: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onStatusUpdate: (Long, String) -> Unit = { _, _ -> }
) {
    val spacing = KhanaBookTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Live online orders", color = MaterialTheme.kbTertiary, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${orders.size} orders from website checkout",
                    color = MaterialTheme.kbPrimary.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FilledTonalIconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.kbBgCard,
                    contentColor = MaterialTheme.kbSecondary
                )
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.kbSecondary
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh online orders")
                }
            }
        }

        error?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(spacing.small))
            Surface(
                color = KbError.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, KbError.copy(alpha = 0.25f))
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.kbSecondary)
                }
            }
            orders.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.kbPrimary.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            "No online orders yet",
                            color = MaterialTheme.kbPrimary.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    stickyHeader {
                        OnlineTableHeader()
                    }
                    items(orders, key = { it.orderId }) { order ->
                        OnlineOrderTableRow(
                            order = order,
                            isUpdating = updatingOrderIds.contains(order.orderId),
                            onViewClick = { onOrderClick(order.orderId) },
                            onStatusUpdate = { nextStatus ->
                                onStatusUpdate(order.orderId, nextStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTableHeader() {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.kbBgCard.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .padding(vertical = spacing.small, horizontal = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Order ID", COL_ONLINE_ORDER_ID)
        HeaderCell("Status", COL_ONLINE_STATUS)
        HeaderCell("View", COL_ONLINE_VIEW)
        HeaderCell("Action", COL_ONLINE_ACTION)
        HeaderCell("Food Ready", COL_ONLINE_READY)
    }
}

@Composable
private fun OnlineOrderTableRow(
    order: MerchantCustomerOrderSummaryResponse,
    isUpdating: Boolean,
    onViewClick: () -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val statusColor = storefrontOrderStatusColor(order.orderStatus)
    val nextStatuses = remember(order.orderStatus) { storefrontNextStatuses(order.orderStatus) }
    val canMarkReady = order.orderStatus.equals("ACCEPTED", ignoreCase = true) ||
                       order.orderStatus.equals("PREPARING", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.hairline)
            .background(
                MaterialTheme.kbBgCard.copy(alpha = 0.35f),
                RoundedCornerShape(4.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(
                order.publicOrderCode,
                COL_ONLINE_ORDER_ID,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.kbPrimary
            )

            Box(modifier = Modifier.weight(COL_ONLINE_STATUS), contentAlignment = Alignment.Center) {
                StatusBadge(
                    order.orderStatus.replace("_", " "),
                    statusColor,
                    Modifier.fillMaxWidth(0.9f)
                )
            }

            Box(modifier = Modifier.weight(COL_ONLINE_VIEW), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = onViewClick,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text(
                        "View",
                        color = MaterialTheme.kbSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Box(modifier = Modifier.weight(COL_ONLINE_ACTION), contentAlignment = Alignment.Center) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.kbSecondary
                    )
                } else if (nextStatuses.isNotEmpty()) {
                    var actionExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { actionExpanded = true }, color = MaterialTheme.kbBgSecondary,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Update",
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = actionExpanded,
                            onDismissRequest = { actionExpanded = false },
                            modifier = Modifier.background(MaterialTheme.kbBgCard)
                        ) {
                            nextStatuses.forEach { status ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            status.replace("_", " "),
                                            color = if (status.equals("REJECTED", ignoreCase = true) || status.equals("CANCELLED", ignoreCase = true)) KbError else MaterialTheme.kbTextPrimary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    onClick = {
                                        onStatusUpdate(status)
                                        actionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "\u2014",
                        color = MaterialTheme.kbPrimary.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(modifier = Modifier.weight(COL_ONLINE_READY), contentAlignment = Alignment.Center) {
                if (canMarkReady && !isUpdating) {
                    Surface(
                        onClick = { onStatusUpdate("READY") },
                        color = KbSuccess.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, KbSuccess.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "Ready",
                            color = KbSuccess,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else if (order.orderStatus.equals("READY", ignoreCase = true)) {
                    Text(
                        "Done",
                        color = KbSuccess,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        "\u2014",
                        color = MaterialTheme.kbPrimary.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                order.customerName,
                color = MaterialTheme.kbPrimary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                CurrencyUtils.formatPrice(order.totalAmount),
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun OnlineOrderDetailsDialog(
    order: MerchantCustomerOrderDetailResponse,
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onStatusUpdate: (String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.kbBgCard,
        titleContentColor = MaterialTheme.kbSecondary,
        textContentColor = MaterialTheme.kbTextPrimary,
        title = {
            Column {
                Text(order.publicOrderCode, color = MaterialTheme.kbPrimary)
                Text(
                    order.customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.kbPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                DetailRow("Status", order.orderStatus.replace("_", " "))
                DetailRow("Payment", "${order.paymentMethod} \u2022 ${order.paymentStatus}")
                DetailRow("Fulfillment", order.fulfillmentType.replace("_", " "))
                order.customerPhone?.takeIf { it.isNotBlank() }?.let { DetailRow("Phone", it) }
                order.customerNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Note", it) }
                DetailRow("Created", DateUtils.formatDisplay(order.createdAt))
                HorizontalDivider(color = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.2f))
                order.items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${item.quantity} x ${item.itemName}${item.variantName?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            CurrencyUtils.formatPrice(item.lineTotal),
                            color = MaterialTheme.kbPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        item.specialInstruction?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MaterialTheme.kbPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.2f))
                DetailRow("Total", CurrencyUtils.formatPrice(order.totalAmount))
                val nextStatuses = remember(order.orderStatus) { storefrontNextStatuses(order.orderStatus) }
                if (nextStatuses.isNotEmpty()) {
                    Text("Update status", color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        nextStatuses.forEach { nextStatus ->
                            AssistChip(
                                onClick = { onStatusUpdate(nextStatus) },
                                enabled = !isUpdating,
                                label = { Text(nextStatus.replace("_", " ")) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = storefrontOrderStatusColor(nextStatus).copy(alpha = 0.18f),
                                    labelColor = MaterialTheme.kbTextPrimary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.kbSecondary)
            }
        }
    )
}

@Composable
private fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            color = color,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun storefrontOrderStatusColor(status: String): Color {
    return when (status.uppercase(Locale.getDefault())) {
        "PENDING_CONFIRMATION" -> KbBrandSaffron
        "ACCEPTED" -> KbWarning
        "PREPARING" -> SwiggyOrange
        "READY" -> KbSuccess
        "COMPLETED" -> KbSuccess
        "REJECTED", "CANCELLED" -> KbError
        else -> MaterialTheme.kbTextSecondary
    }
}

private fun storefrontNextStatuses(currentStatus: String): List<String> {
    return when (currentStatus.uppercase(Locale.getDefault())) {
        "PENDING_CONFIRMATION" -> listOf("ACCEPTED", "REJECTED", "CANCELLED")
        "ACCEPTED" -> listOf("PREPARING", "READY", "CANCELLED")
        "PREPARING" -> listOf("READY", "CANCELLED")
        "READY" -> listOf("COMPLETED", "CANCELLED")
        else -> emptyList()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}
