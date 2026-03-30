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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.ReportsViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.khanabook.lite.pos.domain.util.*

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
                .padding(bottom = 8.dp)
        ) {
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryGold
                    )
                }
                Text(
                    text = "Report Details",
                    modifier = Modifier.weight(1f),
                    color = PrimaryGold,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.width(48.dp))
            }

            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            
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
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        DateRangePicker(
                            state = dateRangePickerState,
                            modifier = Modifier.fillMaxWidth(),
                            title = {
                                Text(
                                    text = "Select Custom Range",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 8.dp),
                                    textAlign = TextAlign.Center,
                                    color = PrimaryGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
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
                                        .padding(start = 8.dp, bottom = 16.dp)
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
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            
            
            if (reportType == "Payment") {
                PaymentLevelView(paymentBreakdown, settingsViewModel)
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    OrderLevelView(orderLevelRows) { billId ->
                        selectedBillId = billId
                        viewModel.loadBillDetails(billId)
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryGold)
            }
        }

        
        selectedBillId?.let {
            OrderDetailsDialog(
                billWithItems = selectedBillDetails,
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
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) PrimaryGold else Color.Transparent,
        border = if (isSelected) null else BorderStroke(1.dp, BorderGold),
        contentColor = if (isSelected) DarkBrown1 else TextLight
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun PaymentLevelView(breakdown: Map<String, String>, settingsViewModel: com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel) {
    val profile by settingsViewModel.profile.collectAsState()
    
    val enabledModes = profile?.let { com.khanabook.lite.pos.domain.manager.PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
    
    
    val mainModes = enabledModes.filter { !com.khanabook.lite.pos.domain.manager.PaymentModeManager.isPartPayment(it) }
    val partModes = enabledModes.filter { com.khanabook.lite.pos.domain.manager.PaymentModeManager.isPartPayment(it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
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
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            
            
            val chunkedPartModes = partModes.chunked(2)
            items(chunkedPartModes) { rowModes ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun PaymentModeItem(mode: String, amount: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, BorderGold.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            Surface(
                modifier = Modifier.size(24.dp),
                color = Color.Transparent
            ) {
                
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(mode, color = TextLight, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("₹${"%.0f".format(amount)}", color = TextLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextGold,
                modifier = Modifier.size(20.dp)
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = GreenReportBg.copy(alpha = 0.4f)),
        border = BorderStroke(0.5.dp, BorderGold.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("$label | ₹${"%.0f".format(totalAmount)}", color = VegGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "₹${"%.0f".format(part1Amount)} ($part1Label) + ₹${"%.0f".format(part2Amount)} ($part2Label)",
                color = TextLight.copy(alpha = 0.8f),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun OrderLevelView(rows: List<com.khanabook.lite.pos.domain.model.OrderLevelRow>, onViewDetails: (Long) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeaderCell("D.ID", Modifier.weight(0.8f))
            HeaderCell("Mode", Modifier.weight(1.8f))
            HeaderCell("Status", Modifier.weight(1.2f))
            HeaderCell("Action", Modifier.weight(1f))
            HeaderCell("Date", Modifier.weight(1.2f))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(rows) { row ->
                OrderRowItem(row, onViewDetails)
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

@Composable
fun OrderRowItem(row: com.khanabook.lite.pos.domain.model.OrderLevelRow, onViewDetails: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, BorderGold.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(row.dailyId, color = TextLight, fontSize = 13.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
            
            Box(modifier = Modifier.weight(1.8f), contentAlignment = Alignment.Center) {
                val (color, label) = when (row.paymentMode) {
                    PaymentMode.ZOMATO -> ZomatoRed to "Zomato"
                    PaymentMode.SWIGGY -> SwiggyOrange to "Swiggy"
                    PaymentMode.CASH -> CashBrown to "Cash"
                    PaymentMode.UPI -> UpiPurple to "UPI"
                    PaymentMode.PART_CASH_UPI -> PartPaymentGreen to "Part-Payment\n(Cash+UPI)"
                    else -> UnknownPaymentGrey to row.paymentMode.displayLabel
                }
                Surface(
                    color = color,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        label,
                        color = Color.White,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        lineHeight = 10.sp
                    )
                }
            }
            
            Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                val statusText = when(row.orderStatus) {
                    OrderStatus.DRAFT -> "Pending"
                    else -> row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                val statusColor = when(row.orderStatus) {
                    OrderStatus.COMPLETED -> VegGreen
                    OrderStatus.CANCELLED -> ZomatoRed
                    else -> TextGold
                }
                Text(
                    statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, PrimaryGold),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onViewDetails(row.billId) }
                ) {
                    Text(
                        "View",
                        color = TextLight,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            
            Text(
                formatDate(row.date),
                color = TextLight,
                fontSize = 11.sp,
                modifier = Modifier.weight(1.2f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun OrderDetailsDialog(
    billWithItems: BillWithItems?, 
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBrown2),
            border = BorderStroke(1.dp, PrimaryGold),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Order Details",
                        color = PrimaryGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = PrimaryGold)
                    }
                }
                
                HorizontalDivider(color = BorderGold.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                if (billWithItems == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = PrimaryGold
                    )
                } else {
                    val bill = billWithItems.bill
                    val items = billWithItems.items

                    DetailRow("Order ID:", "#${bill.dailyOrderDisplay.split("-").last()}")
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Date:", DateUtils.formatDisplay(bill.createdAt))
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Items:", color = TextGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (items.isEmpty()) {
                        Text(
                            text = "No items found in this order.",
                            color = TextLight.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally)
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "${item.itemName} x${item.quantity}",
                                            color = TextLight.copy(alpha = 0.9f),
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Text(
                                            text = CurrencyUtils.formatPrice(item.itemTotal),
                                            color = TextLight,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderGold.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    DetailRow("Total Amount:", CurrencyUtils.formatPrice(bill.totalAmount), PrimaryGold, FontWeight.Bold)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val statusValue = OrderStatus.fromDbValue(bill.orderStatus)
                    val statusText = when(statusValue) {
                        OrderStatus.DRAFT -> "Pending"
                        else -> statusValue.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    val statusColor = when(statusValue) {
                        OrderStatus.COMPLETED -> VegGreen
                        OrderStatus.CANCELLED -> ZomatoRed
                        else -> TextGold
                    }
                    DetailRow("Status:", statusText, statusColor)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    DetailRow("Payment Mode:", PaymentMode.fromDbValue(bill.paymentMode).displayLabel)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)
                ) {
                    Text("Close", color = DarkBrown1)
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
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = TextGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = fontWeight,
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


