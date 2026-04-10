@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.DateUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.ReportsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
    settingsViewModel: com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel = hiltViewModel()
) {
    val reportType by viewModel.reportType.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val paymentBreakdown by viewModel.paymentBreakdown.collectAsState()
    val orderLevelRows by viewModel.orderLevelRows.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val spacing = KhanaBookTheme.spacing
    
    var selectedBillId by remember { mutableStateOf<Long?>(null) }
    val selectedBillDetails by viewModel.selectedBillDetails.collectAsState()
    
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    LaunchedEffect(Unit) {
        viewModel.setTimeFilter("Daily")
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
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryGold
                    )
                }
                Text(
                    text = "Report Details",
                    modifier = Modifier.align(Alignment.Center),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                listOf("Daily", "Weekly", "Monthly", "Custom").forEach { filter ->
                    FilterChip(
                        label = filter,
                        isSelected = timeFilter == filter,
                        onClick = { 
                            if (filter == "Custom") {
                                showDateRangePicker = true
                            } else {
                                viewModel.setTimeFilter(filter) 
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            
            if (showDateRangePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDateRangePicker = false },
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
                            enabled = dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null
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

            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                ReportTypeToggle(
                    label = "Payment Level Report",
                    isSelected = reportType == "Payment",
                    onClick = { viewModel.setReportType("Payment") },
                    modifier = Modifier.weight(1f)
                )
                ReportTypeToggle(
                    label = "Order Level Report",
                    isSelected = reportType == "Order",
                    onClick = { viewModel.setReportType("Order") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            
            if (reportType == "Payment") {
                PaymentLevelView(paymentBreakdown, settingsViewModel)
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    OrderLevelView(orderLevelRows, profile) { billId ->
                        selectedBillId = billId
                        viewModel.loadBillDetails(billId)
                    }
                }
            }
        }

        KhanaBookLoadingOverlay(
            visible = isLoading,
            type = LoadingType.GENERAL,
            message = "Loading reports..."
        )

        
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
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

@Composable
fun ReportTypeToggle(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) BrownSelected.copy(alpha = 0.8f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, PrimaryGold) else BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f)),
        contentColor = if (isSelected) PrimaryGold else TextGold.copy(alpha = 0.7f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun PaymentLevelView(breakdown: Map<String, String>, settingsViewModel: com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel) {
    val profile by settingsViewModel.profile.collectAsState()
    val spacing = KhanaBookTheme.spacing
    
    val enabledModes = profile?.let { com.khanabook.lite.pos.domain.manager.PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
    
    val mainModes = enabledModes.filter { !com.khanabook.lite.pos.domain.manager.PaymentModeManager.isPartPayment(it) }
    val partModes = enabledModes.filter { com.khanabook.lite.pos.domain.manager.PaymentModeManager.isPartPayment(it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(bottom = spacing.medium)
    ) {
        items(mainModes) { mode ->
            PaymentModeItem(
                mode = mode.displayLabel,
                amount = breakdown[mode.displayLabel]?.toDoubleOrNull() ?: 0.0
            )
        }

        if (partModes.isNotEmpty()) {
            item {
                Text(
                    "Part-Payment",
                    color = TextGold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = spacing.small, bottom = spacing.extraSmall)
                )
            }

            val chunkedPartModes = partModes.chunked(2)
            items(chunkedPartModes) { rowModes ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    rowModes.forEach { mode ->
                        val labels = com.khanabook.lite.pos.domain.manager.PaymentModeManager.getPartLabels(mode)
                        PartPaymentCard(
                            label = mode.displayLabel,
                            totalAmount = breakdown[mode.displayLabel]?.toDoubleOrNull() ?: 0.0,
                            part1Amount = breakdown["${mode.displayLabel}_part1"]?.toDoubleOrNull() ?: 0.0,
                            part2Amount = breakdown["${mode.displayLabel}_part2"]?.toDoubleOrNull() ?: 0.0,
                            part1Label = labels.first,
                            part2Label = labels.second,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    if (rowModes.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(spacing.bottomListPadding)) }
    }
}

@Composable
fun PaymentModeItem(mode: String, amount: Double) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(spacing.medium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = PrimaryGold.copy(alpha = 0.5f),
                modifier = Modifier.size(iconSize.medium)
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Text(mode, color = TextLight, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(CurrencyUtils.formatPrice(amount), color = PrimaryGold, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextGold,
                modifier = Modifier.size(iconSize.small)
            )
        }
    }
}

@Composable
fun PartPaymentCard(
    label: String, 
    totalAmount: Double, 
    part1Amount: Double, 
    part2Amount: Double,
    part1Label: String,
    part2Label: String,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = GreenReportBg.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(spacing.small)) {
            Text("$label | ${CurrencyUtils.formatPrice(totalAmount)}", color = VegGreen, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${CurrencyUtils.formatPrice(part1Amount)} ($part1Label) + ${CurrencyUtils.formatPrice(part2Amount)} ($part2Label)",
                color = TextLight.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
            )
        }
    }
}

@Composable
fun OrderLevelView(rows: List<com.khanabook.lite.pos.domain.model.OrderLevelRow>, profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?, onViewDetails: (Long) -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Column(modifier = Modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCell("Order Id", Modifier.weight(1.2f))
            HeaderCell("Status", Modifier.weight(1.8f))
            HeaderCell("Action", Modifier.weight(1.2f))
            HeaderCell("Date", Modifier.weight(1.8f))
        }

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = TextGold.copy(alpha = 0.25f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        "No orders in this period",
                        color = TextGold.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = spacing.bottomListPadding)
            ) {
                items(rows) { row ->
                    OrderRowItem(row, profile, onViewDetails)
                }
            }
        }
    }
}

@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextGold,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

@Composable
fun OrderRowItem(row: com.khanabook.lite.pos.domain.model.OrderLevelRow, profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?, onViewDetails: (Long) -> Unit) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onViewDetails(row.billId) },
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.dailyId, color = TextLight, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
            
            Column(modifier = Modifier.weight(1.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                val statusValue = row.orderStatus
                val statusText = when(statusValue) {
                    OrderStatus.DRAFT -> "Pending"
                    else -> statusValue.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                val statusColor = when(statusValue) {
                    OrderStatus.COMPLETED -> VegGreen
                    OrderStatus.CANCELLED -> ZomatoRed
                    else -> TextGold
                }
                Text(
                    statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 9.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (statusValue == OrderStatus.CANCELLED && row.cancelReason.isNotEmpty()) {
                    Text(
                        row.cancelReason,
                        color = ZomatoRed.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            
            Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, PrimaryGold),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "View",
                        color = TextLight,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            
            Text(
                formatDate(row.date),
                color = TextLight,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun OrderDetailsDialog(
    billWithItems: BillWithItems?,
    profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?,
    onDismiss: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        KhanaBookCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 900.dp)
                .padding(spacing.medium),
            colors = CardDefaults.cardColors(containerColor = DarkBrown2),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Order Details",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = PrimaryGold)
                    }
                }

                HorizontalDivider(color = BorderGold.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(spacing.medium))

                if (billWithItems == null) {
                    // Skeleton loading for bill details
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ShimmerBox(height = 14.dp, modifier = Modifier.fillMaxWidth(0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(height = 14.dp, modifier = Modifier.fillMaxWidth(0.4f))
                        Spacer(modifier = Modifier.height(16.dp))
                        ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        repeat(3) {
                            SkeletonListItem()
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ShimmerBox(height = 20.dp, modifier = Modifier.fillMaxWidth(0.5f))
                    }
                } else {
                    val bill = billWithItems.bill
                    val items = billWithItems.items

                    DetailRow("Order Id:", "#${bill.dailyOrderDisplay.split("-").last()}")
                    Spacer(modifier = Modifier.height(spacing.small))
                    val invoiceLabel = if (profile?.gstEnabled == true) "Tax Invoice No:" else "Invoice No:"
                    DetailRow(invoiceLabel, "INV${bill.lifetimeOrderId}")
                    Spacer(modifier = Modifier.height(spacing.small))
                    DetailRow("Date:", DateUtils.formatDisplay(bill.createdAt))

                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text("Items:", color = TextGold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(spacing.small))

                    if (items.isEmpty()) {
                        Text(
                            text = "No items found in this order.",
                            color = TextLight.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = spacing.medium).align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp, max = 200.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                items(items) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.itemName} x${item.quantity}",
                                            color = TextLight.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f).padding(end = spacing.small)
                                        )
                                        Text(
                                            text = CurrencyUtils.formatPrice(item.itemTotal),
                                            color = TextLight,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))
                    HorizontalDivider(color = BorderGold.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(spacing.medium))
                    DetailRow("Payment Mode:", PaymentMode.fromDbValue(bill.paymentMode).displayLabel)
                    Spacer(modifier = Modifier.height(spacing.small))
                    DetailRow("Total Amount:", CurrencyUtils.formatPrice(bill.totalAmount), PrimaryGold, FontWeight.Bold)
                    Spacer(modifier = Modifier.height(spacing.medium))

                    val statusValue = OrderStatus.fromDbValue(bill.orderStatus)
                    val statusText = when (statusValue) {
                        OrderStatus.DRAFT -> "Pending"
                        else -> statusValue.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    val statusColor = when (statusValue) {
                        OrderStatus.COMPLETED -> VegGreen
                        OrderStatus.CANCELLED -> ZomatoRed
                        else -> TextGold
                    }

                    DetailRow("Status:", statusText, statusColor)
                    if (statusValue == OrderStatus.CANCELLED && bill.cancelReason.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(spacing.small))
                        DetailRow("Cancel Reason:", bill.cancelReason, ZomatoRed, FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(spacing.medium))


                }

                Spacer(modifier = Modifier.height(spacing.large))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", color = DarkBrown1, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = TextLight, fontWeight: FontWeight = FontWeight.Normal) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextGold,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = fontWeight),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

fun formatDate(date: String): String {
    if (date.contains(",")) return date.substringBefore(",").trim() 
    return try {
        val datePart = date.split(" ").getOrNull(0) ?: return date
        val parts = datePart.split("-")
        if (parts.size != 3) return date
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } catch (e: Exception) {
        date
    }
}
