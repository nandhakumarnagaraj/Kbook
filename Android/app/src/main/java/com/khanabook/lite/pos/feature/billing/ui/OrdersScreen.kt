@file:OptIn(
    ExperimentalMaterial3Api::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.khanabook.lite.pos.feature.billing.ui
import com.khanabook.lite.pos.feature.billing.ui.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.core.util.sendInvoiceViaSms
import com.khanabook.lite.pos.core.util.shareInvoiceViaWhatsAppLink
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.reports.ui.OrderDetailsDialog
import com.khanabook.lite.pos.feature.reports.viewmodel.ReportsViewModel
import com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.feature.billing.viewmodel.BillingViewModel
import com.khanabook.lite.pos.feature.printing.ui.printFeedbackKind
import com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

import com.khanabook.lite.pos.feature.billing.ui.*

@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    navController: androidx.navigation.NavController? = null,
    initialSource: String = "ALL",
    highlightedBillId: Long? = null,
    viewModel: ReportsViewModel = hiltViewModel(),
    billingViewModel: BillingViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val allRows by viewModel.orderDetailsTable.collectAsStateWithLifecycle()
    val selectedBillDetails by viewModel.selectedBillDetails.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val timeFilter by viewModel.timeFilter.collectAsStateWithLifecycle()
    val reportError by viewModel.error.collectAsStateWithLifecycle()
    val billingError by billingViewModel.error.collectAsStateWithLifecycle()
    val printStatus by billingViewModel.printStatus.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val spacing = KhanaBookTheme.spacing
    var selectedBillId by remember { mutableStateOf<Long?>(null) }
    var detailCancelBillId by remember { mutableStateOf<Long?>(null) }
    val visibleRows = remember(allRows) {
        allRows.filter { row ->
            row.orderStatus != OrderStatus.DRAFT
        }
    }
    val enabledModes = remember(profile) { 
        profile?.let { com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH) 
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(reportError) {
        reportError?.let { message ->
            KhanaToast.show(message, ToastKind.Error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(billingError) {
        billingError?.let { message ->
            KhanaToast.show(message, ToastKind.Error)
            billingViewModel.clearError()
        }
    }

    LaunchedEffect(printStatus) {
        printStatus?.let { message ->
            KhanaToast.show(message, printFeedbackKind(message))
            billingViewModel.clearPrintStatus()
        }
    }
    val orderListState = rememberLazyListState()
    
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

            val isGstEnabled = profile?.gstEnabled == true

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
                }
            }

            AnimatedVisibility(
                visible = bodyVisible,
                enter = enterSpec,
                exit = exitSpec,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.medium)
                ) {
                    TableHeader(isGstEnabled = isGstEnabled)

                    if (isLoading) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            repeat(10) {
                                SkeletonTableRow(columns = 5)
                                Spacer(modifier = Modifier.height(spacing.hairline))
                            }
                        }
                    } else if (visibleRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.extraLarge),
                            contentAlignment = Alignment.Center
                        ) {
                            KhanaEmptyState(
                                title = "No orders in this period",
                                message = "Try another date filter.",
                                icon = Icons.Default.Description
                            )
                        }
                    } else {
                        LaunchedEffect(highlightedBillId, visibleRows) {
                            val highlightedIndex = highlightedBillId?.let { billId ->
                                visibleRows.indexOfFirst { it.billId == billId }
                            } ?: -1
                            if (highlightedIndex >= 0) {
                                orderListState.animateScrollToItem(highlightedIndex)
                            }
                        }
                        LazyColumn(
                            state = orderListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = spacing.hairline, bottom = spacing.medium)
                        ) {
                            items(visibleRows) { row ->
                                var showCancelDialog by remember { mutableStateOf(false) }
                                var pendingPartMode by remember { mutableStateOf<PaymentMode?>(null) }

                                OrderTableRow(
                                    row = row,
                                    enabledModes = enabledModes,
                                    isHighlighted = row.billId == highlightedBillId,
                                    onClick = {
                                        if (row.orderStatus == OrderStatus.DRAFT) {
                                            navController?.navigate("new_bill?draftBillId=${row.billId}&targetStep=2")
                                        } else {
                                            selectedBillId = row.billId
                                            viewModel.loadBillDetails(row.billId)
                                        }
                                    },
                                    onShare = {
                                        scope.launch {
                                            viewModel.getOrderDetail(row.billId)?.let { detail ->
                                                if (detail.bill.serverId == null) {
                                                    sendInvoiceViaSms(context, detail, profile)
                                                } else {
                                                    shareInvoiceViaWhatsAppLink(context, detail, profile)
                                                }
                                            }
                                        }
                                    },
                                    onShareText = {
                                        scope.launch {
                                            viewModel.getOrderDetail(row.billId)?.let { detail ->
                                                shareInvoiceViaWhatsAppLink(context, detail, profile)
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
        }

        selectedBillId?.let {
            OrderDetailsDialog(
                billWithItems = selectedBillDetails,
                profile = profile,
                onDismiss = {
                    selectedBillId = null
                    viewModel.clearBillDetails()
                },
                onShareInvoice = { detail ->
                    if (detail.bill.serverId == null) {
                        sendInvoiceViaSms(context, detail, profile)
                    } else {
                        shareInvoiceViaWhatsAppLink(context, detail, profile)
                    }
                },
                onPrintReceipt = { detail -> billingViewModel.printReceipt(detail) },
                onResumeDraft = { detail ->
                    navController?.navigate("new_bill?draftBillId=${detail.bill.id}&targetStep=2")
                },
                onCancelOrder = { detail -> detailCancelBillId = detail.bill.id }
            )
        }

        detailCancelBillId?.let { billId ->
            CancelOrderDialog(
                onDismiss = { detailCancelBillId = null },
                onConfirm = { reason ->
                    viewModel.cancelOrder(billId, reason)
                    detailCancelBillId = null
                    selectedBillId = null
                    viewModel.clearBillDetails()
                }
            )
        }


    }
}
