@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.feature.reports.ui
import com.khanabook.lite.pos.core.components.CustomDateRangePickerDialog
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.util.openBillToPrint
import com.khanabook.lite.pos.core.util.shareInvoice
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.reports.viewmodel.ReportsViewModel
import com.khanabook.lite.pos.feature.reports.ui.FilterChip
import com.khanabook.lite.pos.feature.reports.ui.OrderDateFilterComponent
import com.khanabook.lite.pos.feature.reports.ui.OrderHistoryTableComponent
import com.khanabook.lite.pos.feature.reports.ui.OrderSearchBar
import com.khanabook.lite.pos.feature.reports.ui.PaymentLevelView
import com.khanabook.lite.pos.feature.reports.ui.OrderLevelView
import com.khanabook.lite.pos.feature.reports.ui.OrderDetailsDialog
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager
import com.khanabook.lite.pos.feature.billing.ui.CancelOrderDialog
import com.khanabook.lite.pos.feature.billing.ui.PartAmountDialog
import com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
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
    val layout = KhanaBookTheme.layout
    val context = LocalContext.current
    val canViewFullReports = remember { viewModel.canViewFullReports() }

    LaunchedEffect(reportError) {
        reportError?.let { message ->
            KhanaToast.show(message, ToastKind.Error)
            viewModel.clearError()
        }
    }

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

    // Local search over the already-loaded rows: order number, invoice number,
    // customer details, bill amount, payment mode, order type, and channel.
    // No extra query hits Room, so typing stays instant and works offline.
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val filteredRows = remember(orderLevelRows, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            orderLevelRows
        } else {
            orderLevelRows.filter { row ->
                row.dailyId.contains(query, ignoreCase = true) ||
                    row.invoiceDisplay.contains(query, ignoreCase = true) ||
                    row.customerName.contains(query, ignoreCase = true) ||
                    row.customerPhone.contains(query, ignoreCase = true) ||
                    row.totalAmount.contains(query, ignoreCase = true) ||
                    row.paymentMode.displayLabel.contains(query, ignoreCase = true) ||
                    row.paymentMode.dbValue.contains(query, ignoreCase = true) ||
                    row.paymentMode.name.contains(query, ignoreCase = true) ||
                    row.orderType.contains(query, ignoreCase = true) ||
                    row.sourceChannel.contains(query, ignoreCase = true)
            }
        }
    }
    
    var showDateRangePicker by remember { mutableStateOf(false) }
    val hasLoadedOnce by viewModel.hasLoadedOnce.collectAsStateWithLifecycle()

    // The day window is computed per filter change. Refreshing it on resume keeps "Today"
    // correct when the screen is restored after midnight instead of showing yesterday's range.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCurrentFilter()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso))),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layout.maxContentWidth)
                .padding(bottom = spacing.small)
        ) {
            
            AnimatedVisibility(visible = headerVisible, enter = enterSpec, exit = exitSpec) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(layout.topBarIconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(layout.topBarIconSize),
                            tint = PrimaryGold
                        )
                    }
                    Text(
                        text = "Orders",
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryGold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(
                        onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) searchQuery = ""
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(layout.topBarIconButtonSize)
                    ) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchExpanded) "Close search" else "Search orders",
                            modifier = Modifier.size(layout.topBarIconSize),
                            tint = PrimaryGold
                        )
                    }
                }
            }

            
            AnimatedVisibility(visible = contentVisible, enter = enterSpec, exit = exitSpec) {
                OrderDateFilterComponent(
                    selectedFilter = timeFilter,
                    onFilterSelected = { filter ->
                        if (filter == "Custom") {
                            showDateRangePicker = true
                        } else {
                            viewModel.setTimeFilter(filter)
                        }
                    },
                    canViewFullReports = canViewFullReports
                )
            }

            // One gap, not two. The search field used to sit between two unconditional
            // 16dp spacers, so the table paid for the field's spacing even when it was
            // collapsed — 32dp of air above the header for a block that wasn't there.
            Spacer(modifier = Modifier.height(if (searchExpanded) spacing.small else spacing.smallMedium))

            
            if (showDateRangePicker) {
                CustomDateRangePickerDialog(
                    showQuickPresets = false,
                    onDismiss = { showDateRangePicker = false },
                    onConfirm = viewModel::setCustomDateRange
                )
            }

            
            AnimatedVisibility(
                visible = searchExpanded && contentVisible,
                enter = enterSpec,
                exit = exitSpec
            ) {
                OrderSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }

            // Show skeleton while loading, otherwise show content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Skeleton only on the first load. A refresh (cancel, status/payment-mode
                // change, filter change) must keep the table composed, otherwise the
                // LazyColumn is disposed and the orders list jumps back to the top.
                if (isLoading && !hasLoadedOnce) {
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
                    OrderHistoryTableComponent(
                        rows = filteredRows,
                        profile = profile,
                        enabledModes = enabledModes,
                        compactLayout = !layout.isWideListDetail,
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
        }

        // KhanaBookLoadingOverlay retained only for bill detail fetch (dialog)
        // Main list loading is now handled by SkeletonReportScreen above

        selectedBillId?.let {
            OrderDetailsDialog(
                billWithItems = selectedBillDetails,
                profile = profile,
                enabledPaymentModes = enabledModes,
                onDismiss = {
                    selectedBillId = null
                    viewModel.clearBillDetails()
                },
                onPrintReceipt = { detail ->
                    openBillToPrint(context, detail, profile)
                },
                onShareInvoice = { detail ->
                    // Online -> WhatsApp link, offline -> SMS text, quick bill -> share sheet.
                    shareInvoice(context, detail, profile)
                },
                onCancelOrder = { detail ->
                    cancelBillId = detail.bill.id
                },
                onChangePaymentMode = { detail, mode ->
                    if (PaymentModeManager.isPartPayment(mode)) {
                        pendingPartBill = orderLevelRows.find { it.billId == detail.bill.id }
                        pendingPartMode = mode
                    } else {
                        viewModel.updatePaymentMode(detail.bill.id, mode.dbValue)
                    }
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
