@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var headerVisible by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))
    LaunchedEffect(Unit) {
        headerVisible = true
        kotlinx.coroutines.delay(80)
        contentVisible = true
    }
    
    var selectedBillId by remember { mutableStateOf<Long?>(null) }
    val selectedBillDetails by viewModel.selectedBillDetails.collectAsState()
    
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.setTimeFilter("Daily")
    }

    val totalSales = remember(paymentBreakdown) {
        paymentBreakdown.entries
            .filter { !it.key.contains("_part") }
            .sumOf { it.value.toDoubleOrNull() ?: 0.0 }
    }
    val orderCount = remember(orderLevelRows) { orderLevelRows.size }
    val avgOrder = remember(totalSales, orderCount) {
        if (orderCount > 0) totalSales / orderCount else 0.0
    }
    val cancelledCount = remember(orderLevelRows) {
        orderLevelRows.count { it.orderStatus == OrderStatus.CANCELLED }
    }
    val cancelRate = remember(cancelledCount, orderCount) {
        if (orderCount > 0) (cancelledCount.toDouble() / orderCount * 100) else 0.0
    }
    val activeCount = remember(orderLevelRows) {
        orderLevelRows.count { it.orderStatus == OrderStatus.DRAFT }
    }
    val paymentModes = remember(paymentBreakdown) {
        paymentBreakdown.entries
            .filter { !it.key.contains("_part") }
            .mapNotNull { entry ->
                val amount = entry.value.toDoubleOrNull() ?: 0.0
                if (amount > 0.0) entry.key to amount else null
            }
            .sortedByDescending { it.second }
    }
    val topPaymentMode = paymentModes.firstOrNull()?.first ?: "None"
    val topPaymentShare = remember(totalSales, paymentModes) {
        val top = paymentModes.firstOrNull()?.second ?: 0.0
        if (totalSales > 0.0) (top / totalSales) * 100 else 0.0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgGradient)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Reports",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            onClick = { showDateRangePicker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = timeFilter,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            val isWideScreen = !KhanaBookTheme.layout.isCompact

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    // Left column: 2x2 stats grid + popular items
                    Column(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            StatCard(
                                label = "Total Sales",
                                value = CurrencyUtils.formatPrice(totalSales),
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                iconTint = KbBrandSaffron,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "Order Count",
                                value = "$orderCount",
                                icon = Icons.Default.ShoppingCart,
                                iconTint = KbBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            StatCard(
                                label = "Average Order",
                                value = CurrencyUtils.formatPrice(avgOrder),
                                icon = Icons.Default.Description,
                                iconTint = KbSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "Cancel Rate",
                                value = "%.1f%%".format(cancelRate),
                                icon = Icons.Default.Close,
                                iconTint = KbBrandRed,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        PopularItemsCard(
                            orderLevelRows = orderLevelRows,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Right column: revenue chart card (taller on wide screens)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        RevenueChartCard(
                            modifier = Modifier.fillMaxWidth(),
                            chartHeight = 220.dp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    StatCard(
                        label = "Total Sales",
                        value = CurrencyUtils.formatPrice(totalSales),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconTint = KbBrandSaffron,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Order Count",
                        value = "$orderCount",
                        icon = Icons.Default.ShoppingCart,
                        iconTint = KbBlue,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.small))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    StatCard(
                        label = "Average Order",
                        value = CurrencyUtils.formatPrice(avgOrder),
                        icon = Icons.Default.Description,
                        iconTint = KbSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Cancel Rate",
                        value = "%.1f%%".format(cancelRate),
                        icon = Icons.Default.Close,
                        iconTint = KbBrandRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.small))

                RevenueChartCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                )

                Spacer(modifier = Modifier.height(spacing.small))

                PopularItemsCard(
                    orderLevelRows = orderLevelRows,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium)
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            AnimatedContent(
                targetState = reportType,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "filter_crossfade"
            ) { _ ->
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
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            
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
                            enabled = dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null
                        ) {
                            Text("OK", color = MaterialTheme.kbTextPrimary)
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
                                    color = MaterialTheme.kbSecondary,
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
                                weekdayContentColor = MaterialTheme.kbPrimary,
                                dayContentColor = MaterialTheme.kbTextPrimary,
                                selectedDayContainerColor = MaterialTheme.kbPrimary,
                                selectedDayContentColor = MaterialTheme.kbTextOnBrand,
                                todayContentColor = MaterialTheme.kbPrimary
                            )
                        )
                    }
                }
            }

            
            AnimatedVisibility(visible = contentVisible, enter = enterSpec, exit = exitSpec) {
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
            }

            ReportInsightStrip(
                reportType = reportType,
                totalSales = totalSales,
                orderCount = orderCount,
                cancelRate = cancelRate,
                activeCount = activeCount,
                topPaymentMode = topPaymentMode,
                topPaymentShare = topPaymentShare,
                paymentModes = paymentModes,
                modifier = Modifier.padding(horizontal = spacing.medium)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = spacing.medium),
                color = MaterialTheme.kbOutlineSubtle,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    SkeletonReportScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (reportType == "Payment") {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        PaymentMixSummaryCard(
                            paymentModes = paymentModes,
                            totalSales = totalSales,
                            modifier = Modifier.padding(horizontal = spacing.medium)
                        )
                        PaymentLevelView(
                            breakdown = paymentBreakdown,
                            settingsViewModel = settingsViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        OrderStatusHeatmapCard(
                            orderLevelRows = orderLevelRows,
                            modifier = Modifier.padding(horizontal = spacing.medium)
                        )
                        OrderLevelView(orderLevelRows, profile) { billId ->
                            selectedBillId = billId
                            viewModel.loadBillDetails(billId)
                        }
                    }
                }
            }

            ReportDownloadBottomBar(
                onDownloadClick = {
                    scope.launch {
                        try {
                            isExporting = true
                            val file = viewModel.exportReport(context, "PDF", profile)
                            if (!file.exists() || file.length() == 0L) {
                                android.widget.Toast.makeText(context, "Report export failed — empty file", android.widget.Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            val pdfUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, pdfUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Report"))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Report export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        } finally {
                            isExporting = false
                        }
                    }
                },
                isExporting = isExporting
            )
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
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(spacing.medium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconTint.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(spacing.small))
            Column {
                Text(
                    text = value,
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun RevenueChartCard(
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 100.dp
) {
    val spacing = KhanaBookTheme.spacing
    val barData = remember {
        val cal = Calendar.getInstance()
        (6 downTo 0).map { daysAgo ->
            cal.timeInMillis - daysAgo * 86400000L
        }.map { millis ->
            val sdf = SimpleDateFormat("E", Locale.getDefault())
            val dayLabel = sdf.format(Date(millis)).take(3)
            val height = (0.2f + java.util.Random().nextFloat() * 0.6f)
            dayLabel to height
        }
    }

    KhanaBookCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Text(
                text = "Revenue",
                color = MaterialTheme.kbSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(spacing.smallMedium))
            Text(
                text = "Last 7 Days",
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(spacing.smallMedium))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                barData.forEach { (label, heightFraction) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height((chartHeight.value * 0.8f * heightFraction).dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(KbBrandSaffron, KbBrandSaffron.copy(alpha = 0.3f))
                                    ),
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = MaterialTheme.kbTextTertiary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopularItemsCard(
    orderLevelRows: List<com.khanabook.lite.pos.domain.model.OrderLevelRow>,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing

    KhanaBookCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            Text(
                text = "Popular Items",
                color = MaterialTheme.kbSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(spacing.small))

            val completedCount = orderLevelRows.count { it.orderStatus == OrderStatus.COMPLETED }
            val cancelledCount = orderLevelRows.count { it.orderStatus == OrderStatus.CANCELLED }

            if (orderLevelRows.isEmpty()) {
                Text(
                    text = "No order data available",
                    color = MaterialTheme.kbTextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = spacing.medium)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ItemStatChip(
                        label = "Completed",
                        value = "$completedCount",
                        color = KbSuccess
                    )
                    ItemStatChip(
                        label = "Cancelled",
                        value = "$cancelledCount",
                        color = KbBrandRed
                    )
                    ItemStatChip(
                        label = "Total",
                        value = "${orderLevelRows.size}",
                        color = KbBrandSaffron
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemStatChip(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = MaterialTheme.kbTextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ReportInsightStrip(
    reportType: String,
    totalSales: Double,
    orderCount: Int,
    cancelRate: Double,
    activeCount: Int,
    topPaymentMode: String,
    topPaymentShare: Double,
    paymentModes: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val revenuePerOrder = if (orderCount > 0) totalSales / orderCount else 0.0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            InsightPill(Modifier.weight(1f), "Revenue / order", CurrencyUtils.formatPrice(revenuePerOrder), KbBrandSaffron)
            InsightPill(Modifier.weight(1f), "Cancellation", "%.1f%%".format(cancelRate), if (cancelRate > 8.0) KbWarning else KbSuccess)
            InsightPill(
                Modifier.weight(1f),
                if (reportType == "Payment") "Top payment" else "Active orders",
                if (reportType == "Payment") topPaymentMode else activeCount.toString(),
                if (reportType == "Payment") KbSuccess else KbWarning
            )
            InsightPill(
                Modifier.weight(1f),
                if (reportType == "Payment") "Mix share" else "Order count",
                if (reportType == "Payment") "%.0f%%".format(topPaymentShare) else orderCount.toString(),
                MaterialTheme.kbSecondary
            )
        }

        if (reportType == "Payment" && paymentModes.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                items(paymentModes.take(5), key = { it.first }) { (mode, amount) ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = KbBrandSaffron.copy(alpha = KbOpacity.StatusBg),
                        border = BorderStroke(1.dp, KbBrandSaffron.copy(alpha = KbOpacity.StatusBorder))
                    ) {
                        Text(
                            text = "$mode  ${CurrencyUtils.formatPrice(amount)}",
                            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                            color = KbBrandSaffron,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    tone: Color
) {
    KhanaBookCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Text(
                text = value,
                color = tone,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PaymentMixSummaryCard(
    paymentModes: List<Pair<String, Double>>,
    totalSales: Double,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Text(
                text = "Payment mix",
                color = MaterialTheme.kbSecondary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            paymentModes.take(4).forEach { (mode, amount) ->
                val share = if (totalSales > 0.0) (amount / totalSales) * 100 else 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = mode, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(text = "${CurrencyUtils.formatPrice(amount)} • %.0f%%".format(share), color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OrderStatusHeatmapCard(
    orderLevelRows: List<com.khanabook.lite.pos.domain.model.OrderLevelRow>,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val total = orderLevelRows.size.coerceAtLeast(1)
    val completed = orderLevelRows.count { it.orderStatus == OrderStatus.COMPLETED }
    val active = orderLevelRows.count { it.orderStatus == OrderStatus.DRAFT }
    val cancelled = orderLevelRows.count { it.orderStatus == OrderStatus.CANCELLED }
    KhanaBookCard(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Text(
                text = "Status heatmap",
                color = MaterialTheme.kbSecondary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), modifier = Modifier.fillMaxWidth()) {
                HeatTile("Active", active, total, KbWarning, Modifier.weight(1f))
                HeatTile("Done", completed, total, KbSuccess, Modifier.weight(1f))
                HeatTile("Cancelled", cancelled, total, KbError, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeatTile(
    label: String,
    count: Int,
    total: Int,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val fill = if (total > 0) count.toFloat() / total.toFloat() else 0f
    Column(
        modifier = modifier
            .background(tone.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.kbOutlineSubtle, RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                    .background(tone, RoundedCornerShape(999.dp))
            )
        }
        Text(text = count.toString(), color = tone, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.kbPrimary else Color.Transparent,
        animationSpec = tween(200),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.kbTextOnBrand else MaterialTheme.kbTextPrimary,
        animationSpec = tween(200),
        label = "chip_content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
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
    val spacing = KhanaBookTheme.spacing
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.kbPrimaryBold.copy(alpha = 0.8f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.kbPrimary) else BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f)),
        contentColor = if (isSelected) MaterialTheme.kbPrimary else MaterialTheme.kbPrimary.copy(alpha = 0.7f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = spacing.extraSmall)
            )
        }
    }
}

@Composable
fun PaymentLevelView(
    breakdown: Map<String, String>,
    settingsViewModel: com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val profile by settingsViewModel.profile.collectAsState()
    val spacing = KhanaBookTheme.spacing
    
    val enabledModes = profile?.let { com.khanabook.lite.pos.domain.manager.PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
    
    val mainModes = enabledModes.filter { !com.khanabook.lite.pos.domain.manager.PaymentModeManager.isPartPayment(it) }
    val partModes = enabledModes.filter { com.khanabook.lite.pos.domain.manager.PaymentModeManager.isPartPayment(it) }

    LazyColumn(
        modifier = modifier
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
                    color = MaterialTheme.kbPrimary,
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
        
        item { Spacer(modifier = Modifier.height(spacing.small)) }
    }
}

@Composable
fun ReportDownloadBottomBar(
    onDownloadClick: () -> Unit,
    isExporting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.small, bottom = spacing.small)
    ) {
        Button(
            onClick = onDownloadClick,
            enabled = !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .padding(horizontal = spacing.medium),
            colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.kbTextOnBrand
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    "Exporting...",
                    color = MaterialTheme.kbTextOnBrand,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.kbTextOnBrand,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text(
                    "Download Reports",
                    color = MaterialTheme.kbTextOnBrand,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun PaymentModeItem(mode: String, amount: Double) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    
    KhanaBookGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {}
    ) {
        Row(
            modifier = Modifier
                .padding(spacing.medium)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(KbBrandSaffron.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.kbSecondary,
                    modifier = Modifier.size(iconSize.small)
                )
            }
            Spacer(modifier = Modifier.width(spacing.medium))
            Text(mode, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrencyUtils.formatPrice(amount),
                    color = MaterialTheme.kbSecondary,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    "Total Sales",
                    color = MaterialTheme.kbPrimary.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
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
            Text("$label | ${CurrencyUtils.formatPrice(totalAmount)}", color = KbSuccess, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(spacing.hairline))
            Text(
                "${CurrencyUtils.formatPrice(part1Amount)} ($part1Label) + ${CurrencyUtils.formatPrice(part2Amount)} ($part2Label)",
                color = MaterialTheme.kbTextPrimary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
            )
        }
    }
}

@Composable
fun OrderLevelView(rows: List<com.khanabook.lite.pos.domain.model.OrderLevelRow>, profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?, onViewDetails: (Long) -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium)
                .background(MaterialTheme.kbBgCard.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCell("Order No", Modifier.weight(1.2f))
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
                        tint = MaterialTheme.kbPrimary.copy(alpha = 0.25f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Text(
                        "No orders in this period",
                        color = MaterialTheme.kbPrimary.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                contentPadding = PaddingValues(bottom = spacing.small)
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
        color = MaterialTheme.kbPrimary,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.dailyId, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
            
            Column(modifier = Modifier.weight(1.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                val statusValue = row.orderStatus
                val statusText = when(statusValue) {
                    OrderStatus.DRAFT -> "Pending"
                    else -> statusValue.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                val statusColor = when(statusValue) {
                    OrderStatus.COMPLETED -> KbSuccess
                    OrderStatus.CANCELLED -> KbError
                    else -> MaterialTheme.kbPrimary
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
                    border = BorderStroke(1.dp, MaterialTheme.kbPrimary),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "View",
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = spacing.small - spacing.hairline, vertical = spacing.hairline)
                    )
                }
            }
            
            Text(
                formatDate(row.date),
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OrderStatusChip(status: OrderStatus) {
    if (status == OrderStatus.DRAFT) return

    val (label, color) = when (status) {
        OrderStatus.COMPLETED -> "COMPLETED" to KbPurpleAccent
        OrderStatus.CANCELLED -> "CANCELLED" to KbError
        else -> "ACTIVE" to Color(0xFF0284C7)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
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
private fun DetailRowLight(label: String, value: String, isStatus: Boolean = false, status: OrderStatus = OrderStatus.DRAFT) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (isStatus) {
            OrderStatusChip(status)
        } else {
            Text(text = value, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
fun OrderDetailsDialog(
    billWithItems: BillWithItems?,
    profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?,
    onDismiss: () -> Unit,
    onPrintKds: ((BillWithItems) -> Unit)? = null,
    onPrintReceipt: ((BillWithItems) -> Unit)? = null
) {
    val spacing = KhanaBookTheme.spacing
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.kbBgPrimary
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Dark purple gradient header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.kbHeaderGradient)
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            text = "Order Detail",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (billWithItems != null) {
                            val statusValue = OrderStatus.fromDbValue(billWithItems.bill.orderStatus)
                            OrderStatusChip(statusValue)
                        } else {
                            Box(modifier = Modifier.size(40.dp))
                        }
                    }

                    if (billWithItems != null) {
                        val bill = billWithItems.bill
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "#KB-${bill.dailyOrderDisplay.split("-").last()}",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "INV${bill.lifetimeOrderId} · ${PaymentMode.fromDbValue(bill.paymentMode).displayLabel}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = CurrencyUtils.formatPrice(bill.totalAmount),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp)
                            )
                            Text(
                                text = DateUtils.formatDisplay(bill.createdAt),
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Scrollable White/Light background Content area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.kbBgPrimary)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (billWithItems == null) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = KbBrandSaffron)
                        }
                    } else {
                        val bill = billWithItems.bill
                        val items = billWithItems.items

                        // ORDER ITEMS Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "ORDER ITEMS",
                                    color = KbBrandSaffron,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${item.quantity}×  ${item.itemName}",
                                            color = MaterialTheme.kbTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = CurrencyUtils.formatPrice(item.itemTotal),
                                            color = MaterialTheme.kbTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.kbOutlineSubtle, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                val subtotalVal = bill.subtotal.toDoubleOrNull() ?: 0.0
                                val totalVal = bill.totalAmount.toDoubleOrNull() ?: 0.0
                                val taxVal = totalVal - subtotalVal

                                DetailRowLight("Subtotal", CurrencyUtils.formatPrice(bill.subtotal))
                                if (taxVal > 0) {
                                    val taxPct = bill.gstPercentage.toDoubleOrNull() ?: 0.0
                                    val taxLabel = if (taxPct > 0) "Tax (${taxPct.toInt()}%)" else "Tax"
                                    DetailRowLight(taxLabel, CurrencyUtils.formatPrice(taxVal.toString()))
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Total",
                                        color = MaterialTheme.kbTextPrimary,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        CurrencyUtils.formatPrice(bill.totalAmount),
                                        color = KbBrandSaffron,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // PAYMENT DETAILS Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "PAYMENT DETAILS",
                                    color = KbBrandSaffron,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                DetailRowLight("Method", PaymentMode.fromDbValue(bill.paymentMode).displayLabel)
                                DetailRowLight("Transaction ID", "INV${bill.lifetimeOrderId}")
                                DetailRowLight("Time", DateUtils.formatDisplay(bill.createdAt))

                                val statusValue = OrderStatus.fromDbValue(bill.orderStatus)
                                val statusText = when (statusValue) {
                                    OrderStatus.DRAFT -> "Pending"
                                    else -> statusValue.name.lowercase().replaceFirstChar { it.uppercase() }
                                }
                                DetailRowLight("Status", statusText, isStatus = true, status = statusValue)
                            }
                        }

                        // ── Action Buttons (inside scroll — always visible, never clipped) ──
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPrintKds?.invoke(billWithItems) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = BorderStroke(1.dp, KbPurpleAccent),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reprint KOT", color = KbPurpleAccent, style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = { onPrintReceipt?.invoke(billWithItems) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reprint Bill", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
            }
        }
    }
}

private fun formatDateRangeHeadline(startMillis: Long?, endMillis: Long?): String {
    val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val startText = startMillis?.let { formatter.format(Date(it)) } ?: "Start date"
    val endText = endMillis?.let { formatter.format(Date(it)) } ?: "End date"
    return "$startText - $endText"
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
