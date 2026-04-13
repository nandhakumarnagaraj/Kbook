@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.DateUtils
import com.khanabook.lite.pos.domain.util.shareBillOnWhatsApp
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.ReportsViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
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
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val allRows by viewModel.orderDetailsTable.collectAsState()
    val selectedBillDetails by viewModel.selectedBillDetails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val haptic = LocalHapticFeedback.current
    val spacing = KhanaBookTheme.spacing
    var selectedBillId by remember { mutableStateOf<Long?>(null) }
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
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, Color.Black)))
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

                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium)) {
                        TableHeader(isGstEnabled = profile?.gstEnabled == true)
                    }
                }
            }

            if (isLoading) {
                AnimatedVisibility(visible = bodyVisible, enter = enterSpec, exit = exitSpec, modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium)
                    ) {
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
                        contentPadding = PaddingValues(top = spacing.small, bottom = spacing.bottomListPadding)
                    ) {
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

private const val TABLE_TOTAL_WEIGHT = 8.0f

@Composable
fun TableHeader(isGstEnabled: Boolean) {
    val spacing = KhanaBookTheme.spacing
    val invoiceHeader = if (isGstEnabled) "Tax Invoice No" else "Invoice No"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .padding(vertical = spacing.small, horizontal = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Order No", 1.0f / TABLE_TOTAL_WEIGHT)
        HeaderCell(invoiceHeader, 2.0f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Mode", 1.6f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Status", 1.8f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Date", 1.6f / TABLE_TOTAL_WEIGHT)
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShareText
            )
            .padding(vertical = spacing.hairline)
            .background(
                Color.Black.copy(alpha = if (isCancelled) 0.15f else 0.25f),
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
                row.dailyNo, 1.0f / TABLE_TOTAL_WEIGHT,
                color = if (isCancelled) TextLight.copy(alpha = 0.35f) else TextLight
            )
            TableCell(
                "INV${row.lifetimeNo}", 2.0f / TABLE_TOTAL_WEIGHT,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) TextLight.copy(alpha = 0.35f) else TextLight
            )

            Box(modifier = Modifier.weight(1.6f / TABLE_TOTAL_WEIGHT), contentAlignment = Alignment.Center) {
                val color = if (isCancelled) Color.Gray else getPayModeColor(row.payMode)
                Surface(
                    onClick = { if (!isCancelled) payModeExpanded = true },
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

            Box(modifier = Modifier.weight(1.8f / TABLE_TOTAL_WEIGHT), contentAlignment = Alignment.Center) {
                val statusColor = when (row.orderStatus) {
                    OrderStatus.COMPLETED -> SuccessGreen
                    OrderStatus.CANCELLED -> DangerRed
                    else -> Color.Gray
                }
                Surface(
                    onClick = { if (!isCancelled) statusExpanded = true },
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
                if (!isCancelled) {
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

            TableCell(DateUtils.formatDisplayDate(row.salesDate), 1.6f / TABLE_TOTAL_WEIGHT, fontSize = 9.sp)
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
                OutlinedTextField(
                    value = p1Text,
                    onValueChange = { p1Text = it },
                    label = { Text("${labels.first} Amount", color = TextGold.copy(alpha = 0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
        TextButton(
            onClick = { onConfirm(p1Text, p2Text) },
            enabled = isValid
        ) {
            Text("Confirm", color = PrimaryGold, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextGold, style = MaterialTheme.typography.labelLarge)
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
        PaymentMode.POS -> PosPurple 
        PaymentMode.ZOMATO -> VegGreen 
        PaymentMode.SWIGGY -> SwiggyOrange 
        else -> OtherPaymentGrey
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
