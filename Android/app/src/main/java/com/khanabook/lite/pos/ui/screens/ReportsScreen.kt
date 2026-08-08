@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens

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
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.ReportsViewModel
import com.khanabook.lite.pos.ui.screens.reports.FilterChip
import com.khanabook.lite.pos.ui.screens.reports.ReportTypeToggle
import com.khanabook.lite.pos.ui.screens.reports.PaymentLevelView
import com.khanabook.lite.pos.ui.screens.reports.ReportDownloadBottomBar
import com.khanabook.lite.pos.ui.screens.reports.OrderLevelView
import com.khanabook.lite.pos.ui.screens.reports.OrderDetailsDialog

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
    val reportError by viewModel.error.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current

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
