@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.DateUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.ReportsViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
    settingsViewModel: com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel = hiltViewModel()
) {
    val reportType by viewModel.reportType.collectAsStateWithLifecycle()
    val timeFilter by viewModel.timeFilter.collectAsStateWithLifecycle()
    val paymentBreakdown by viewModel.paymentBreakdown.collectAsStateWithLifecycle()
    val orderLevelRows by viewModel.orderLevelRows.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Staggered entry animation — same pattern used across all screens
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
    val selectedBillDetails by viewModel.selectedBillDetails.collectAsStateWithLifecycle()
    
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.setTimeFilter("Daily")
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
            }

            
            AnimatedVisibility(visible = contentVisible, enter = enterSpec, exit = exitSpec) {
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
                CustomDateRangePickerDialog(
                    state = dateRangePickerState,
                    onDismiss = { showDateRangePicker = false },
                    onConfirm = viewModel::setCustomDateRange
                )
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

            Spacer(modifier = Modifier.height(spacing.medium))

            // Show skeleton while loading, otherwise show content
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
                    PaymentLevelView(
                        breakdown = paymentBreakdown,
                        settingsViewModel = settingsViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    OrderLevelView(
                        rows = orderLevelRows,
                        profile = profile
                    ) { billId ->
                        selectedBillId = billId
                        viewModel.loadBillDetails(billId)
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
                                KhanaToast.show("Report export failed - empty file", ToastKind.Error)
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
                            KhanaToast.show("Report export failed: ${e.message}", ToastKind.Error)
                        } finally {
                            isExporting = false
                        }
                    }
                },
                isExporting = isExporting
            )
        }

        // KhanaBookLoadingOverlay retained only for bill detail fetch (dialog)
        // Main list loading is now handled by SkeletonReportScreen above

        
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
    val spacing = KhanaBookTheme.spacing
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
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
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
        KhanaPrimaryButton(
            text = if (isExporting) "Exporting..." else "Download Reports",
            onClick = onDownloadClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium),
            enabled = !isExporting,
            isLoading = isExporting,
            leadingIcon = Icons.Default.Download,
            height = 44.dp
        )
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
            Spacer(modifier = Modifier.height(spacing.hairline))
            Text(
                "${CurrencyUtils.formatPrice(part1Amount)} ($part1Label) + ${CurrencyUtils.formatPrice(part2Amount)} ($part2Label)",
                color = TextLight.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)


            )
        }
    }
}

private val COL_ORDER   = 1.2f
private val COL_STATUS  = 1.8f
private val COL_ACTION  = 1.2f
private val COL_DATE    = 1.8f

private fun com.khanabook.lite.pos.domain.model.OrderLevelRow.displaySourceOrModeLabel(): String {
    return when (sourceChannel.trim().lowercase()) {
        "zomato" -> PaymentMode.ZOMATO.displayLabel
        "swiggy" -> PaymentMode.SWIGGY.displayLabel
        "own_website", "own website" -> PaymentMode.OWN_WEBSITE.displayLabel
        else -> paymentMode.displayLabel
    }
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

@Composable
fun OrderLevelView(rows: List<com.khanabook.lite.pos.domain.model.OrderLevelRow>, profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?, onViewDetails: (Long) -> Unit) {
    val spacing = KhanaBookTheme.spacing
    val invoiceHeader = if (profile?.gstEnabled == true) "Tax Inv No" else "Invoice No"
    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium)
                .background(DarkBrown1.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCell("Order No", COL_ORDER)
            HeaderCell("Status", COL_STATUS)
            HeaderCell("Action", COL_ACTION)
            HeaderCell("Date", COL_DATE)
        }

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                KhanaEmptyState(
                    title = "No orders in this period",
                    message = "Try another date or report filter.",
                    icon = Icons.Default.Description
                )
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
fun OrderRowItem(row: com.khanabook.lite.pos.domain.model.OrderLevelRow, profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?, onViewDetails: (Long) -> Unit) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onViewDetails(row.billId) },
        colors = CardDefaults.cardColors(containerColor = DarkBrown1.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(row.dailyId, COL_ORDER)
            
            Box(modifier = Modifier.weight(COL_STATUS), contentAlignment = Alignment.Center) {
                val statusValue = row.orderStatus
                val statusText = when (statusValue) {
                    OrderStatus.DRAFT -> "Pending"
                    else -> statusValue.name.lowercase().replaceFirstChar { it.uppercase() }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    KhanaStatusBadge(
                        text = statusText,
                        kind = when (statusValue) {
                            OrderStatus.COMPLETED -> KhanaStatusKind.Success
                            OrderStatus.CANCELLED -> KhanaStatusKind.Danger
                            else -> KhanaStatusKind.Warning
                        }
                    )
                    if (statusValue == OrderStatus.CANCELLED && row.cancelReason.isNotEmpty()) {
                        Text(
                            row.cancelReason,
                            color = DangerRed.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(COL_ACTION), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, PrimaryGold),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "View",
                        color = TextLight,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = spacing.small - spacing.hairline, vertical = spacing.hairline)
                    )
                }
            }
            
            TableCell(formatDate(row.date), COL_DATE, fontSize = 9.sp)
        }
    }
}
@Composable
fun OrderDetailsDialog(
    billWithItems: BillWithItems?,
    profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?,
    onDismiss: () -> Unit,
    onShareInvoice: ((BillWithItems) -> Unit)? = null,
    onPrintReceipt: ((BillWithItems) -> Unit)? = null,
    onResumeDraft: ((BillWithItems) -> Unit)? = null,
    onCancelOrder: ((BillWithItems) -> Unit)? = null
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
            colors = CardDefaults.cardColors(containerColor = DarkBrown1),
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
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ShimmerBox(height = 14.dp, modifier = Modifier.fillMaxWidth(0.5f))
                        Spacer(modifier = Modifier.height(spacing.small))
                        ShimmerBox(height = 14.dp, modifier = Modifier.fillMaxWidth(0.4f))
                        Spacer(modifier = Modifier.height(spacing.medium))
                        ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.3f))
                        Spacer(modifier = Modifier.height(spacing.small))
                        repeat(3) {
                            SkeletonListItem()
                            Spacer(modifier = Modifier.height(spacing.extraSmall))
                        }
                        Spacer(modifier = Modifier.height(spacing.smallMedium))
                        ShimmerBox(height = 20.dp, modifier = Modifier.fillMaxWidth(0.5f))
                    }
                } else {
                    val bill = billWithItems.bill
                    val items = billWithItems.items

                    DetailRow("Order No:", "#${bill.dailyOrderDisplay.split("-").last()}", valueColor = PrimaryGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(spacing.small))
                    val invoiceLabel = if (profile?.gstEnabled == true) "Tax Invoice No:" else "Invoice No:"
                    DetailRow(invoiceLabel, "INV${bill.lifetimeOrderId}")
                    Spacer(modifier = Modifier.height(spacing.small))
                    DetailRow("Date:", DateUtils.formatDisplay(bill.createdAt))

                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text("Items:", color = TextGold, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
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
                    DetailRow("Total Amount:", CurrencyUtils.formatPrice(bill.totalAmount), valueColor = PrimaryGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(spacing.small))

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

                    DetailRow("Status:", statusText, valueColor = statusColor, fontWeight = FontWeight.Bold)
                    if (statusValue == OrderStatus.CANCELLED && bill.cancelReason.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(spacing.small))
                        DetailRow("Cancel Reason:", bill.cancelReason, valueColor = ZomatoRed, fontWeight = FontWeight.Bold)
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
                    Text("Close", color = DarkBrown1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun DetailStatusRow(label: String, statusText: String, kind: KhanaStatusKind) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextGold,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(end = spacing.small)
        )
        KhanaStatusBadge(text = statusText, kind = kind)
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color = TextLight, fontWeight: FontWeight = FontWeight.Normal) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextGold,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(end = spacing.small)
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
