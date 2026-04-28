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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.khanabook.lite.pos.domain.util.shareBillOnWhatsApp
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

    LaunchedEffect(Unit) {
        viewModel.setTimeFilter("Daily")
    }
    LaunchedEffect(selectedSource) {
        if (selectedSource == "ONLINE" && onlineOrders.isEmpty()) {
            storefrontOrdersViewModel.loadOrders()
        }
    }

    // Standard staggered entry animation
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
                    Text("OK", color = PrimaryGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel", color = PrimaryGold)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkBrown2)
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
                            color = PrimaryGold,
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
                            color = PrimaryGold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                colors = DatePickerDefaults.colors(
                    containerColor = DarkBrown2,
                    titleContentColor = PrimaryGold,
                    headlineContentColor = PrimaryGold,
                    weekdayContentColor = TextGold,
                    dayContentColor = TextLight,
                    selectedDayContainerColor = PrimaryGold,
                    selectedDayContentColor = DarkBrown1,
                    todayContentColor = PrimaryGold
                )
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = spacing.small)
        ) {
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryGold)
                    }
                    Text(
                        text = "Order Details",
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryGold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Column {
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
                            onClick = { selectedSource = "ONLINE" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))

                    if (selectedSource == "POS") {
                    PeriodTabs(
                        selectedFilter = timeFilter,
                        onTabSelected = {
                            if (it == "Custom") {
                                showDateRangePicker = true
                            } else {
                                viewModel.setTimeFilter(it)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(spacing.medium))
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
                        TableHeader(isGstEnabled = isGstEnabled)
                        repeat(10) {
                            SkeletonTableRow(columns = 5)
                            Spacer(modifier = Modifier.height(2.dp))
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
                                tint = TextGold.copy(alpha = 0.25f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(KhanaBookTheme.spacing.small))
                            Text(
                                "No orders in this period",
                                color = TextGold.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec, modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium),
                        contentPadding = PaddingValues(top = spacing.small, bottom = spacing.medium)
                    ) {
                        stickyHeader {
                            TableHeader(isGstEnabled = isGstEnabled)
                        }
                        items(allRows) { row ->
                            var showCancelDialog by remember { mutableStateOf(false) }
                            var pendingPartMode by remember { mutableStateOf<PaymentMode?>(null) }

                            OrderTableRow(
                                row = row,
                                enabledModes = enabledModes,
                                onClick = {
                                    selectedBillId = row.billId
                                    viewModel.loadBillDetails(row.billId)
                                },
                                onShare = {
                                    scope.launch {
                                        viewModel.getOrderDetail(row.billId)?.let { detail ->
                                            shareBillOnWhatsApp(context, detail, profile)
                                        }
                                    }
                                },
                                onShareText = {
                                    scope.launch {
                                        viewModel.getOrderDetail(row.billId)?.let { detail ->
                                            com.khanabook.lite.pos.domain.util.shareBillTextOnWhatsApp(context, detail, profile)
                                        }
                                    }
                                },
                                onRequestCancel = { showCancelDialog = true },
                                onStatusChange = { newStatus ->
                                    onStatusChange(row.billId, newStatus)
                                },
                                onPayModeChange = { newMode ->
                                    if (PaymentModeManager.isPartPayment(newMode)) {
                                        pendingPartMode = newMode
                                    } else {
                                        viewModel.updatePaymentMode(row.billId, newMode.dbValue)
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
fun OrderFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryGold else Color.Transparent,
        animationSpec = tween(200),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) DarkBrown1 else TextLight,
        animationSpec = tween(200),
        label = "chip_content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, BorderGold),
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelMedium
            )
        }
    }
}

// Column weights sized to content: Order No (short num) | Invoice (INV+num) | Mode (badge) | Status (badge) | Date
private val COL_ORDER   = 0.9f
private val COL_INVOICE = 1.7f
private val COL_MODE    = 1.7f
private val COL_STATUS  = 2.0f
private val COL_DATE    = 1.7f

@Composable
fun TableHeader(isGstEnabled: Boolean) {
    val spacing = KhanaBookTheme.spacing
    val invoiceHeader = if (isGstEnabled) "Tax Inv No" else "Invoice No"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBrown1.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .padding(vertical = spacing.small, horizontal = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Order No", COL_ORDER)
        HeaderCell(invoiceHeader, COL_INVOICE)
        HeaderCell("Mode", COL_MODE)
        HeaderCell("Status", COL_STATUS)
        HeaderCell("Date", COL_DATE)
    }
}

@Composable
fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = TextGold,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShareText
            )
            .padding(vertical = spacing.hairline)
            .background(
                DarkBrown1.copy(alpha = if (isCancelled) 0.15f else 0.35f),
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
                row.dailyNo, COL_ORDER,
                color = if (isCancelled) TextLight.copy(alpha = 0.35f) else TextLight
            )
            TableCell(
                "INV${row.lifetimeNo}", COL_INVOICE,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) TextLight.copy(alpha = 0.35f) else TextLight
            )

            Box(modifier = Modifier.weight(COL_MODE), contentAlignment = Alignment.Center) {
                val color = if (!canEdit) Color.Gray else getPayModeColor(row.payMode)
                Surface(
                    onClick = { if (canEdit) payModeExpanded = true },
                    color = color,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(horizontal = spacing.hairline)
                ) {
                    Text(
                        text = row.payMode.displayLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = payModeExpanded,
                    onDismissRequest = { payModeExpanded = false },
                    modifier = Modifier.background(DarkBrown2)
                ) {
                    enabledModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayLabel, color = TextLight, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onPayModeChange(mode); payModeExpanded = false }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(COL_STATUS), contentAlignment = Alignment.Center) {
                val statusColor = when (row.orderStatus) {
                    OrderStatus.COMPLETED -> SuccessGreen
                    OrderStatus.CANCELLED -> DangerRed
                    else -> TextMuted
                }
                Surface(
                    onClick = { if (canEdit) statusExpanded = true },
                    color = statusColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(horizontal = spacing.extraSmall)
                ) {
                    Text(
                        text = when (row.orderStatus) {
                            OrderStatus.COMPLETED -> "Completed"
                            OrderStatus.CANCELLED -> "Cancelled"
                            else -> "Draft"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
                        lineHeight = 10.sp
                    )
                }
                if (canEdit) {
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                        modifier = Modifier.background(DarkBrown2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Completed", color = TextLight, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onStatusChange(OrderStatus.COMPLETED.dbValue); statusExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)) },
                            onClick = { statusExpanded = false; onRequestCancel() }
                        )
                    }
                }
            }

            TableCell(DateUtils.formatDisplayDate(row.salesDate), COL_DATE, fontSize = 9.sp)
        }

        if (isCancelled && row.cancelReason.isNotBlank()) {
            Text(
                text = "Reason: ${row.cancelReason}",
                color = DangerRed.copy(alpha = 0.7f),
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
                selectedAccent = DangerRed,
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
                Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
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
    val totalVal = totalAmount.toDoubleOrNull() ?: 0.0
    val half = totalVal / 2.0
    var p1Text by remember { mutableStateOf("%.2f".format(half)) }
    var p2Text by remember { mutableStateOf("%.2f".format(totalVal - half)) }

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
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = p1Text,
                        onValueChange = { p1Text = it },
                        label = { Text("${labels.first} Amount", color = TextGold.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        isError = !isValid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = p2Text,
                        onValueChange = { p2Text = it },
                        label = { Text("${labels.second} Amount", color = TextGold.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        isError = !isValid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
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
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextGold, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = { onConfirm(p1Text, p2Text) },
            enabled = isValid
        ) {
            Text("Confirm", color = PrimaryGold, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = TextLight
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

private fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> SuccessGreen
        PaymentMode.UPI -> Brown500 
        PaymentMode.POS -> PrimaryGold
        PaymentMode.ZOMATO -> VegGreen
        PaymentMode.SWIGGY -> SwiggyOrange
        else -> Brown500
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

@Composable
private fun OnlineOrdersPane(
    orders: List<MerchantCustomerOrderSummaryResponse>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    error: String?,
    updatingOrderIds: Set<Long>,
    onRefresh: () -> Unit,
    onOrderClick: (Long) -> Unit
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
                Text("Live online orders", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${orders.size} orders from website checkout",
                    color = TextGold.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FilledTonalIconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = DarkBrown2,
                    contentColor = PrimaryGold
                )
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryGold
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh online orders")
                }
            }
        }

        error?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(spacing.small))
            Surface(
                color = DangerRed.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.25f))
            ) {
                Text(
                    text = it,
                    color = TextLight,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryGold)
                }
            }
            orders.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = TextGold.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(spacing.small))
                        Text(
                            "No online orders yet",
                            color = TextGold.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    items(orders, key = { it.orderId }) { order ->
                        OnlineOrderCard(
                            order = order,
                            isUpdating = updatingOrderIds.contains(order.orderId),
                            onClick = { onOrderClick(order.orderId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineOrderCard(
    order: MerchantCustomerOrderSummaryResponse,
    isUpdating: Boolean,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = DarkBrown1.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.publicOrderCode, color = PrimaryGold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        order.customerName,
                        color = TextLight,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    order.customerPhone?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = TextGold, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryGold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                StatusBadge(order.fulfillmentType.replace("_", " "), Brown500, Modifier.weight(1f))
                StatusBadge(order.orderStatus.replace("_", " "), storefrontOrderStatusColor(order.orderStatus), Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    CurrencyUtils.formatPrice(order.totalAmount),
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    DateUtils.formatDisplay(order.createdAt),
                    color = TextGold.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
        containerColor = DarkBrown2,
        titleContentColor = PrimaryGold,
        textContentColor = TextLight,
        title = {
            Column {
                Text(order.publicOrderCode)
                Text(
                    order.customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                DetailRow("Status", order.orderStatus.replace("_", " "))
                DetailRow("Payment", "${order.paymentMethod} • ${order.paymentStatus}")
                DetailRow("Fulfillment", order.fulfillmentType.replace("_", " "))
                order.customerPhone?.takeIf { it.isNotBlank() }?.let { DetailRow("Phone", it) }
                order.customerNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Note", it) }
                DetailRow("Created", DateUtils.formatDisplay(order.createdAt))
                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                order.items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${item.quantity} x ${item.itemName}${item.variantName?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}",
                            color = TextLight,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            CurrencyUtils.formatPrice(item.lineTotal),
                            color = TextGold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        item.specialInstruction?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = TextGold.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                DetailRow("Total", CurrencyUtils.formatPrice(order.totalAmount))
                val nextStatuses = remember(order.orderStatus) { storefrontNextStatuses(order.orderStatus) }
                if (nextStatuses.isNotEmpty()) {
                    Text("Update status", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        nextStatuses.forEach { nextStatus ->
                            AssistChip(
                                onClick = { onStatusUpdate(nextStatus) },
                                enabled = !isUpdating,
                                label = { Text(nextStatus.replace("_", " ")) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = storefrontOrderStatusColor(nextStatus).copy(alpha = 0.18f),
                                    labelColor = TextLight
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PrimaryGold)
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

private fun storefrontOrderStatusColor(status: String): Color {
    return when (status.uppercase(Locale.getDefault())) {
        "PENDING_CONFIRMATION" -> PrimaryGold
        "ACCEPTED" -> Brown500
        "PREPARING" -> SwiggyOrange
        "READY" -> VegGreen
        "COMPLETED" -> SuccessGreen
        "REJECTED", "CANCELLED" -> DangerRed
        else -> TextMuted
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
