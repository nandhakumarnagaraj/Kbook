@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val allRows by viewModel.orderDetailsTable.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val haptic = LocalHapticFeedback.current
    val spacing = KhanaBookTheme.spacing
    val enabledModes = remember(profile) { 
        profile?.let { com.khanabook.lite.pos.domain.manager.PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH) 
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedPeriod by remember { mutableIntStateOf(0) }
    
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    LaunchedEffect(selectedPeriod) {
        if (selectedPeriod != 3) {
            val (from, to) = periodRange(selectedPeriod)
            viewModel.loadReports(from, to)
        }
    }

    fun onStatusChange(billId: Long, newStatus: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.updateOrderStatus(billId, newStatus)
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        viewModel.setCustomDateRange(start, end)
                    }
                    showDateRangePicker = false
                }) {
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
            Box(modifier = Modifier.padding(horizontal = spacing.small)) {
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.fillMaxWidth(),
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
                        DateRangePickerDefaults.DateRangePickerHeadline(
                            selectedStartDateMillis = dateRangePickerState.selectedStartDateMillis,
                            selectedEndDateMillis = dateRangePickerState.selectedEndDateMillis,
                            displayMode = dateRangePickerState.displayMode,
                            dateFormatter = DatePickerDefaults.dateFormatter(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = spacing.small, bottom = spacing.medium)
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
            .background(Brush.verticalGradient(colors = listOf(DarkBrown1, DarkBrown2)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            PeriodTabs(
                selectedTabIndex = selectedPeriod,
                onTabSelected = { 
                    selectedPeriod = it 
                    if (it == 3) showDateRangePicker = true
                }
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            TableHeader()

            if (allRows.isEmpty()) {
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
            } else LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = spacing.medium),
                contentPadding = PaddingValues(top = spacing.small, bottom = spacing.bottomListPadding)
            ) {
                items(allRows) { row ->
                    var showCancelDialog by remember { mutableStateOf(false) }

                    OrderTableRow(
                        row = row,
                        enabledModes = enabledModes,
                        onShare = {
                            scope.launch {
                                viewModel.getOrderDetail(row.billId)?.let { detail ->
                                    shareBillOnWhatsApp(context, detail, profile)
                                }
                            }
                        },
                        onRequestCancel = { showCancelDialog = true },
                        onStatusChange = { newStatus ->
                            onStatusChange(row.billId, newStatus)
                        },
                        onPayModeChange = { newMode ->
                            viewModel.updatePaymentMode(row.billId, newMode.dbValue)
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
                }
            }
        }
    }
}

@Composable
fun PeriodTabs(selectedTabIndex: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Daily", "Weekly", "Monthly", "Custom")
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        tabs.forEachIndexed { index, title ->
            OrderFilterChip(
                label = title,
                isSelected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
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

private const val TABLE_TOTAL_WEIGHT = 9.2f

@Composable
fun TableHeader() {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(vertical = spacing.medium, horizontal = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("D.No", 1f / TABLE_TOTAL_WEIGHT)
        HeaderCell("L.No", 1.2f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Current\nStatus", 1.5f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Amount", 1.3f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Mode", 1.2f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Status", 1.5f / TABLE_TOTAL_WEIGHT)
        HeaderCell("Date", 1.5f / TABLE_TOTAL_WEIGHT)
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
    onShare: () -> Unit,
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
            .padding(vertical = 1.dp)
            .background(if (isCancelled) ParchmentBG.copy(alpha = 0.5f) else ParchmentBG)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(
                row.dailyNo, 1f / TABLE_TOTAL_WEIGHT,
                color = if (isCancelled) Color.Black.copy(alpha = 0.35f) else Color.Black
            )
            TableCell(
                row.lifetimeNo.toString(), 1.2f / TABLE_TOTAL_WEIGHT,
                color = if (isCancelled) Color.Black.copy(alpha = 0.35f) else Color.Black
            )
            TableCell(
                row.currentStatus, 1.5f / TABLE_TOTAL_WEIGHT, fontSize = 10.sp,
                color = if (isCancelled) Color.Black.copy(alpha = 0.35f) else Color.Black
            )
            TableCell(
                CurrencyUtils.formatPrice(row.salesAmount), 1.3f / TABLE_TOTAL_WEIGHT,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) Color.Black.copy(alpha = 0.35f) else Color.Black
            )

            Box(modifier = Modifier.weight(1.2f / TABLE_TOTAL_WEIGHT), contentAlignment = Alignment.Center) {
                val color = if (isCancelled) Color.Gray else getPayModeColor(row.payMode)
                Surface(
                    onClick = { if (!isCancelled) payModeExpanded = true },
                    color = color,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = row.payMode.displayLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = payModeExpanded,
                    onDismissRequest = { payModeExpanded = false },
                    modifier = Modifier.background(ParchmentBG)
                ) {
                    enabledModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayLabel, color = DarkBrown1, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onPayModeChange(mode); payModeExpanded = false }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1.5f / TABLE_TOTAL_WEIGHT), contentAlignment = Alignment.Center) {
                val statusColor = when (row.orderStatus) {
                    OrderStatus.COMPLETED -> SuccessGreen
                    OrderStatus.CANCELLED -> DangerRed
                    else -> Color.Gray
                }
                Surface(
                    onClick = { if (!isCancelled) statusExpanded = true },
                    color = statusColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
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
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        lineHeight = 10.sp
                    )
                }
                if (!isCancelled) {
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                        modifier = Modifier.background(ParchmentBG)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Completed", color = DarkBrown1, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onStatusChange(OrderStatus.COMPLETED.dbValue); statusExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)) },
                            onClick = { statusExpanded = false; onRequestCancel() }
                        )
                    }
                }
            }

            TableCell(DateUtils.formatDisplayDate(row.salesDate), 1.5f / TABLE_TOTAL_WEIGHT, fontSize = 9.sp)
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
    var selectedReason by remember { mutableStateOf("") }
    var customReason by remember { mutableStateOf("") }
    val spacing = KhanaBookTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        title = { Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text("Select a reason:", color = TextLight, style = MaterialTheme.typography.bodyMedium)
                presetReasons.forEach { reason ->
                    val isSelected = selectedReason == reason
                    Surface(
                        onClick = { selectedReason = reason; if (reason != "Other") customReason = "" },
                        color = if (isSelected) DangerRed.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isSelected) DangerRed else BorderGold.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = reason,
                            color = if (isSelected) DangerRed else TextLight,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                        )
                    }
                }
                if (selectedReason == "Other") {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        placeholder = { Text("Describe the reason...", color = TextGold.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DangerRed,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        maxLines = 2
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalReason = if (selectedReason == "Other") customReason.trim() else selectedReason
                    if (finalReason.isNotBlank()) onConfirm(finalReason)
                },
                enabled = selectedReason.isNotBlank() && (selectedReason != "Other" || customReason.isNotBlank())
            ) {
                Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Order", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Black
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
