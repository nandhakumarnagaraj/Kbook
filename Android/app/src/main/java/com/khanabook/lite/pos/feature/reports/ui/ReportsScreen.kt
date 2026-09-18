@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.feature.reports.ui
import com.khanabook.lite.pos.core.components.CustomDateRangePickerDialog
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.util.UserMessageSanitizer
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.reports.viewmodel.ReportsViewModel
import com.khanabook.lite.pos.feature.reports.ui.FilterChip
import com.khanabook.lite.pos.feature.reports.ui.ReportTypeToggle
import com.khanabook.lite.pos.feature.reports.ui.PaymentLevelView
import com.khanabook.lite.pos.feature.reports.ui.ReportDownloadBottomBar
import com.khanabook.lite.pos.feature.reports.ui.OrderLevelView
import com.khanabook.lite.pos.feature.reports.ui.OrderDetailsDialog
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager
import com.khanabook.lite.pos.feature.billing.ui.CancelOrderDialog
import com.khanabook.lite.pos.feature.billing.ui.PartAmountDialog
import com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
    settingsViewModel: com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel = hiltViewModel()
) {
    val reportType by viewModel.reportType.collectAsStateWithLifecycle()
    val timeFilter by viewModel.timeFilter.collectAsStateWithLifecycle()
    val paymentBreakdown by viewModel.paymentBreakdown.collectAsStateWithLifecycle()
    val orderLevelRows by viewModel.orderLevelRows.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val reportError by viewModel.error.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val canViewFullReports = remember { viewModel.canViewFullReports() }
    val canExportReports = remember { viewModel.canExportReports() }

    LaunchedEffect(reportError) {
        reportError?.let { message ->
            KhanaToast.show(message, ToastKind.Error)
            viewModel.clearError()
        }
    }
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
    val enabledModes = remember(profile) {
        profile?.let { PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
    }
    var cancelBillId by remember { mutableStateOf<Long?>(null) }
    var pendingPartMode by remember { mutableStateOf<PaymentMode?>(null) }
    var pendingPartBill by remember { mutableStateOf<OrderLevelRow?>(null) }
    
    var showDateRangePicker by remember { mutableStateOf(false) }

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
                    // Today's summary is part of every staff member's baseline
                    // (reports.day_summary). Anything wider is revenue reporting and
                    // needs reports.full — matching what AnalyticsController enforces
                    // server-side for the same data.
                    val availableFilters = if (canViewFullReports) {
                        listOf("Daily", "Weekly", "Monthly", "Custom")
                    } else {
                        listOf("Daily")
                    }
                    availableFilters.forEach { filter ->
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
                        profile = profile,
                        enabledModes = enabledModes,
                        onStatusChange = { billId, newStatus ->
                            viewModel.updateOrderStatus(billId, newStatus)
                        },
                        onPayModeChange = { billId, newMode ->
                            if (PaymentModeManager.isPartPayment(newMode)) {
                                pendingPartBill = orderLevelRows.find { it.billId == billId }
                                pendingPartMode = newMode
                            } else {
                                viewModel.updatePaymentMode(billId, newMode.dbValue)
                            }
                        },
                        onRequestCancel = { billId ->
                            cancelBillId = billId
                        },
                        onViewDetails = { billId ->
                            selectedBillId = billId
                            viewModel.loadBillDetails(billId)
                        }
                    )
                }
            }

            if (canExportReports) {
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
                            KhanaToast.show(
                                UserMessageSanitizer.sanitize(e, "Report export failed. Please try again."),
                                ToastKind.Error
                            )
                        } finally {
                            isExporting = false
                        }
                    }
                },
                isExporting = isExporting
            )
            }
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
                },
                onCancelOrder = { detail ->
                    cancelBillId = detail.bill.id
                }
            )
        }

        cancelBillId?.let { billId ->
            CancelOrderDialog(
                onDismiss = { cancelBillId = null },
                onConfirm = { reason ->
                    viewModel.cancelOrder(billId, reason)
                    cancelBillId = null
                    if (selectedBillId == billId) {
                        selectedBillId = null
                        viewModel.clearBillDetails()
                    }
                }
            )
        }

        val partBill = pendingPartBill
        val partMode = pendingPartMode
        if (partBill != null && partMode != null) {
            PartAmountDialog(
                mode = partMode,
                totalAmount = partBill.totalAmount,
                onDismiss = {
                    pendingPartBill = null
                    pendingPartMode = null
                },
                onConfirm = { p1, p2 ->
                    viewModel.updatePaymentMode(partBill.billId, partMode.dbValue, p1, p2)
                    pendingPartBill = null
                    pendingPartMode = null
                }
            )
        }
    }
}
