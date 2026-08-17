@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.ui.screens.newbill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import com.khanabook.lite.pos.domain.manager.PaymentRecoveryAssessment
import com.khanabook.lite.pos.domain.manager.PaymentReturnManager
import com.khanabook.lite.pos.domain.manager.QrCodeManager
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.PaymentLimits
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun PaymentStep(
    viewModel: BillingViewModel,
    settingsViewModel: SettingsViewModel,
    onBackToMenu: () -> Unit,
    onComplete: () -> Unit,
    onFailed: () -> Unit = {},
    onFlowLockChange: (Boolean) -> Unit = {},
    resumePendingPayment: Boolean = false,
    onPayOnline: ((serverBillId: Long, restaurantId: Long, amount: String) -> Unit)? = null
) {
    val summary by viewModel.billSummary.collectAsStateWithLifecycle()
    val persistedPaymentTotal by viewModel.persistedPaymentTotal.collectAsStateWithLifecycle()
    val paymentRecovery by viewModel.paymentRecovery.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val enabledModes =
            remember(profile) {
                profile?.let { PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
            }
    val partialRecovery = paymentRecovery as? PaymentRecoveryAssessment.Partial
    val selectableModes = remember(enabledModes, partialRecovery) {
        if (partialRecovery == null) {
            enabledModes
        } else {
            enabledModes.filter {
                it in setOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.POS) &&
                    it.dbValue !in partialRecovery.usedModes
            }
        }
    }
    var selectedMode by remember(selectableModes) {
        mutableStateOf(
            if (selectableModes.contains(PaymentMode.CASH)) {
                PaymentMode.CASH
            } else {
                selectableModes.firstOrNull() ?: PaymentMode.CASH
            }
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var showQrModal by remember { mutableStateOf(false) }
    val restoredPaymentMode by viewModel.paymentMode.collectAsStateWithLifecycle()
    val restoredPartAmount1 by viewModel.partAmount1.collectAsStateWithLifecycle()
    val restoredPartAmount2 by viewModel.partAmount2.collectAsStateWithLifecycle()

    var p1Text by remember { mutableStateOf("") }
    var p2Text by remember { mutableStateOf("") }
    var resumedPendingBillId by remember { mutableStateOf<Long?>(null) }
    var isCreatingPaymentAttempt by remember { mutableStateOf(false) }
    var recoveryAutoFinalizeStarted by remember { mutableStateOf(false) }
    var showResetRecoveryDialog by remember { mutableStateOf(false) }
    val latestPaymentEvent by PaymentReturnManager.latestEvent.collectAsStateWithLifecycle()

    
    LaunchedEffect(selectableModes) {
        if (selectableModes.isNotEmpty()) {
            selectedMode = when {
                selectableModes.contains(PaymentMode.CASH) -> PaymentMode.CASH
                else -> selectableModes.first()
            }
        }
    }

    // Observe Easebuzz payment return events (deep-link / browser flow returns).
// The SDK path returns via savedStateHandle (see NewBillScreen); this only
// records a gateway hint on the resume flow — finalization stays manual.
    LaunchedEffect(latestPaymentEvent) {
        val event = latestPaymentEvent ?: return@LaunchedEffect
        if (event.status == PaymentReturnManager.Status.SUCCESS && resumedPendingBillId != null) {
            viewModel.setGatewayResult(event.txnId, "success")
        } else if (event.status == PaymentReturnManager.Status.FAILURE) {
            PaymentReturnManager.clearLatestEvent()
            KhanaToast.show("Payment was cancelled or failed.", ToastKind.Warning)
        }
    }

    LaunchedEffect(
        resumePendingPayment,
        restoredPaymentMode,
        restoredPartAmount1,
        restoredPartAmount2,
        selectableModes
    ) {
        if (!resumePendingPayment) return@LaunchedEffect
        if (selectableModes.contains(restoredPaymentMode)) {
            selectedMode = restoredPaymentMode
        }
        p1Text = restoredPartAmount1
        p2Text = restoredPartAmount2
    }

    // UPI transaction limit: most Indian banks cap single UPI transactions at ₹1,00,000.
    // Amounts exceeding this should use a split payment mode where UPI covers up to ₹1L
    // and the remainder is collected via another payment method (cash or POS).
    val paymentTotal = persistedPaymentTotal?.takeIf { it.toBigDecimalOrNull() != null }
        ?: summary.total
    val payableNow = partialRecovery?.remainingAmount ?: paymentTotal
    val upiMaxAmount = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.toDouble()
    val totalAmount = payableNow.toDoubleOrNull() ?: 0.0
    val isUpiMode =
            selectedMode == PaymentMode.UPI ||
                    selectedMode == PaymentMode.PART_CASH_UPI ||
                    selectedMode == PaymentMode.PART_UPI_POS

    val isSplitMode =
            selectedMode == PaymentMode.PART_CASH_UPI ||
                    selectedMode == PaymentMode.PART_CASH_POS ||
                    selectedMode == PaymentMode.PART_UPI_POS

    // Auto-switch to split payment when the UPI single-pay amount exceeds the limit.
    // This prevents generating a QR code with an amount that UPI apps will reject.
    val upiExceedsLimit = isUpiMode && !isSplitMode && totalAmount > upiMaxAmount
    LaunchedEffect(upiExceedsLimit) {
        if (upiExceedsLimit) {
            val cashMode = PaymentMode.PART_CASH_UPI
            if (selectableModes.contains(cashMode)) {
                selectedMode = cashMode
            }
        }
    }

    LaunchedEffect(selectedMode, payableNow, resumePendingPayment) {
        if (isSplitMode && !resumePendingPayment) {
            val split = when (selectedMode) {
                PaymentMode.PART_CASH_UPI -> if (totalAmount > upiMaxAmount) {
                    BillCalculator.splitCashUpiWithUpiCap(payableNow)
                } else {
                    BillCalculator.splitPartPayment(payableNow)
                }
                PaymentMode.PART_UPI_POS -> if (totalAmount > upiMaxAmount) {
                    BillCalculator.splitUpiPosWithUpiCap(payableNow)
                } else {
                    BillCalculator.splitPartPayment(payableNow)
                }
                else -> BillCalculator.splitPartPayment(payableNow)
            }
            p1Text = split.first
            p2Text = split.second
        }
    }

    val p1 = p1Text.toDoubleOrNull() ?: 0.0
    val p2 = p2Text.toDoubleOrNull() ?: 0.0
    val upiPayableAmount =
            when (selectedMode) {
                PaymentMode.PART_CASH_UPI -> p2
                PaymentMode.PART_UPI_POS -> p1
                else -> payableNow.toDoubleOrNull() ?: 0.0
            }
    val isAmountValid =
            if (isSplitMode) {
                BillCalculator.validatePartPayment(p1Text, p2Text, payableNow)
            } else true
    val canGenerateAmountQr =
        isUpiMode &&
            isAmountValid &&
            upiPayableAmount > 0.0 &&
            upiPayableAmount <= upiMaxAmount &&
            !profile?.upiHandle.isNullOrBlank()
    val requiresOnlineAttempt = isUpiMode && viewModel.editingBillId == null
    val recoveryConflict = paymentRecovery as? PaymentRecoveryAssessment.Conflicting
    val controlsLocked = recoveryConflict != null
    var isSubmitting by remember { mutableStateOf(false) }
    val paymentAttemptReady =
        recoveryConflict == null &&
            selectableModes.isNotEmpty() &&
            (!requiresOnlineAttempt || resumedPendingBillId != null) &&
            !isSubmitting

    LaunchedEffect(paymentRecovery, viewModel.editingBillId) {
        val billId = viewModel.editingBillId
        if (
            paymentRecovery is PaymentRecoveryAssessment.Complete &&
            billId != null &&
            !recoveryAutoFinalizeStarted
        ) {
            recoveryAutoFinalizeStarted = true
            if (viewModel.finalizeRecoveredPaymentSet(billId)) {
                viewModel.clearActiveSession()
                onComplete()
            } else {
                recoveryAutoFinalizeStarted = false
            }
        }
    }

    LaunchedEffect(canGenerateAmountQr, selectedMode, p1Text, p2Text, resumePendingPayment) {
        if (
            canGenerateAmountQr &&
            requiresOnlineAttempt &&
            !resumePendingPayment &&
            resumedPendingBillId == null &&
            !isCreatingPaymentAttempt
        ) {
            isCreatingPaymentAttempt = true
            try {
                viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                resumedPendingBillId = viewModel.createDraftOnlineBill()
            } finally {
                isCreatingPaymentAttempt = false
            }
        }
    }

    // Keep ViewModel payment state in sync when split amounts change after draft creation.
    // Prevents finalizeOnlineBill from using stale _partAmount1/_partAmount2 values.
    LaunchedEffect(selectedMode, p1Text, p2Text) {
        if (resumedPendingBillId != null || viewModel.editingBillId != null) {
            viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
        }
    }

    val relocationRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    if (showResetRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showResetRecoveryDialog = false },
            title = { Text("Reset payment attempt?") },
            text = {
                Text(
                    "Legacy payment identities will be repaired when possible. Only unsynced manual records can be removed; synced amounts and gateway references are preserved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val billId = viewModel.editingBillId
                        showResetRecoveryDialog = false
                        if (billId != null) {
                            scope.launch {
                                viewModel.resetPaymentRecovery(billId)
                            }
                        }
                    }
                ) {
                    Text("Reset", color = DangerRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetRecoveryDialog = false }) {
                    Text("Keep payment records")
                }
            },
            containerColor = DarkBrown2,
            titleContentColor = TextLight,
            textContentColor = TextGold
        )
    }

    // Generate UPI QR locally with ZXing. This must not wait for the background payment-attempt save.
    val context = LocalContext.current
    val dynamicUpiQrBitmap by produceState<android.graphics.Bitmap?>(
        null,
        profile?.upiHandle,
        profile?.shopName,
        upiPayableAmount,
        canGenerateAmountQr,
        profile?.logoUrl,
        profile?.logoPath
    ) {
        val handle = profile?.upiHandle
        value = if (canGenerateAmountQr && !handle.isNullOrBlank()) {
            val logo = loadShopLogoBlocking(context, profile?.logoUrl, profile?.logoPath)
            withContext(Dispatchers.Default) {
                QrCodeManager.generateUpiQrWithLogo(
                    handle,
                    profile?.shopName ?: "RESTAURANT",
                    upiPayableAmount,
                    logo,
                    512
                )
            }
        } else null
    }

    val showQrCode = isUpiMode
    val isUpiQrLoading =
        showQrCode &&
            canGenerateAmountQr &&
            dynamicUpiQrBitmap == null


    val layout = KhanaBookTheme.layout

    StickyBottomScaffold(
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                if (enabledModes.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (!isAmountValid || isSubmitting) return@Button
                            isSubmitting = true
                            scope.launch {
                                // If EASEBUZZ mode selected, trigger online payment flow
                                if (selectedMode == PaymentMode.EASEBUZZ && onPayOnline != null) {
                                    val localBillId = viewModel.editingBillId
                                        ?: viewModel.createDraftOnlineBill()
                                        ?: run { isSubmitting = false; return@launch }
                                    var serverBillId = viewModel.getBillById(localBillId)?.bill?.serverId
                                    if (serverBillId == null || serverBillId == 0L) {
                                        viewModel.triggerSyncAndWait()
                                        repeat(5) {
                                            kotlinx.coroutines.delay(500L)
                                            serverBillId = viewModel.getBillById(localBillId)?.bill?.serverId
                                            if (serverBillId != null && serverBillId != 0L) return@repeat
                                        }
                                    }
                                    if (serverBillId == null || serverBillId == 0L) {
                                        KhanaToast.show("Bill sync pending. Please wait and try again.", ToastKind.Warning)
                                        isSubmitting = false
                                        return@launch
                                    }
                                    val restaurantId = profile?.restaurantId ?: run { isSubmitting = false; return@launch }
                                    onPayOnline(serverBillId!!, restaurantId, paymentTotal)
                                    return@launch
                                }
                                viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                                val success = when {
                                    resumedPendingBillId != null ->
                                        viewModel.finalizeOnlineBill(resumedPendingBillId!!, PaymentStatus.SUCCESS)
                                    partialRecovery != null && viewModel.editingBillId != null ->
                                        viewModel.recoverPartialDraftPayment(
                                            viewModel.editingBillId!!,
                                            selectedMode
                                        )
                                    viewModel.editingBillId != null ->
                                        viewModel.settleDraftOrder(
                                            billId = viewModel.editingBillId!!,
                                            paymentMode = selectedMode,
                                            status = PaymentStatus.SUCCESS,
                                            partAmount1 = p1Text,
                                            partAmount2 = p2Text
                                        )
                                    else ->
                                        viewModel.completeOrder(PaymentStatus.SUCCESS)
                                }
                                if (success) {
                                    viewModel.clearGatewayResult()
                                    viewModel.clearActiveSession()
                                    onComplete()
                                } else {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KhanaBookTheme.spacing.buttonHeightLarge),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                !isAmountValid -> Color.Gray
                                selectedMode == PaymentMode.EASEBUZZ -> Brown500
                                else -> SuccessGreen
                            }
                        ),
                        shape = KhanaRadii.lg,
                        enabled = isAmountValid && paymentAttemptReady
                    ) {
                        Text(
                            when {
                                selectedMode == PaymentMode.EASEBUZZ -> "Pay Online"
                                partialRecovery != null -> "Confirm Remaining Payment"
                                else -> "Payment Successful"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onPayOnline != null && !enabledModes.contains(PaymentMode.EASEBUZZ)) {
                        Button(
                            onClick = {
                                if (isSubmitting) return@Button
                                isSubmitting = true
                                scope.launch {
                                    val localBillId = viewModel.editingBillId
                                        ?: viewModel.createDraftOnlineBill()
                                        ?: run { isSubmitting = false; return@launch }

                                    // Get the bill's server ID — required for Easebuzz
                                    var serverBillId = viewModel.getBillById(localBillId)?.bill?.serverId
                                    if (serverBillId == null || serverBillId == 0L) {
                                        // Bill not synced yet — trigger sync and wait for serverId
                                        viewModel.triggerSyncAndWait()
                                        // Retry reading serverId up to 5 times with short delays
                                        // (Room write may not be immediately visible)
                                        repeat(5) {
                                            kotlinx.coroutines.delay(500L)
                                            serverBillId = viewModel.getBillById(localBillId)?.bill?.serverId
                                            if (serverBillId != null && serverBillId != 0L) return@repeat
                                        }
                                    }
                                    if (serverBillId == null || serverBillId == 0L) {
                                        KhanaToast.show("Bill sync pending. Please wait and try again.", ToastKind.Warning)
                                        isSubmitting = false
                                        return@launch
                                    }
                                    val restaurantId = profile?.restaurantId ?: run { isSubmitting = false; return@launch }
                                    onPayOnline(serverBillId!!, restaurantId, paymentTotal)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(KhanaBookTheme.spacing.buttonHeightLarge),
                            colors = ButtonDefaults.buttonColors(containerColor = Brown500),
                            shape = KhanaRadii.lg,
                            enabled = isAmountValid && paymentAttemptReady
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text(
                                "Pay Online (Easebuzz)",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (paymentRecovery !is PaymentRecoveryAssessment.Empty) {
                                    onBackToMenu()
                                    return@launch
                                }
                                viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                                when {
                                    resumedPendingBillId != null ->
                                        viewModel.finalizeOnlineBill(resumedPendingBillId!!, PaymentStatus.FAILED, "Customer left")
                                    viewModel.editingBillId != null ->
                                        viewModel.settleDraftOrder(viewModel.editingBillId!!, selectedMode, PaymentStatus.FAILED)
                                    else ->
                                        viewModel.completeOrder(PaymentStatus.FAILED, "Customer left")
                                }
                                viewModel.clearGatewayResult()
                                viewModel.clearActiveSession()
                                PaymentReturnManager.clearLatestEvent()
                                onFailed()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
                        enabled = paymentAttemptReady
                    ) {
                        Text(
                            if (paymentRecovery !is PaymentRecoveryAssessment.Empty) {
                                "Keep Pending & Go Back"
                            } else {
                                "Payment Failed / Cancelled"
                            },
                            color = DangerRed,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) {
    Column(
            modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = layout.contentPadding, vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (enabledModes.isEmpty()) {
            Spacer(modifier = Modifier.height(spacing.huge))
            Icon(
                Icons.Default.Payment,
                null,
                tint = DangerRed.copy(alpha = 0.5f),
                modifier = Modifier.size(KhanaBookTheme.iconSize.heroCircle)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(
                "Please configure the payment option and add payment methods",
                color = DangerRed,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(spacing.large))
            Button(
                onClick = onBackToMenu,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                shape = KhanaRadii.lg
            ) {
                Text("Go Back", color = DarkBrown1)
            }
        } else {
            partialRecovery?.let { recovery ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = WarningYellow.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, WarningYellow.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            "Partial payment found",
                            color = WarningYellow,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Recorded ${CurrencyUtils.formatPrice(recovery.paidAmount)}. Collect only ${CurrencyUtils.formatPrice(recovery.remainingAmount)} to complete this order.",
                            color = TextLight,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.medium))
            }

            recoveryConflict?.let { recovery ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = DangerRed.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            "Payment records need repair",
                            color = DangerRed,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            recovery.reason,
                            color = TextLight,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { showResetRecoveryDialog = true }) {
                            Text("Repair payment records", color = WarningYellow)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.medium))
            }


            if (showQrCode) {
                Text("Scan to Pay", color = PrimaryGold, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(spacing.medium))

                Box(
                    modifier =
                    Modifier.size(layout.qrCodeSize)
                        .background(Color.White, KhanaRadii.lg)
                        .border(2.dp, PrimaryGold, KhanaRadii.lg)
                        .padding(spacing.smallMedium)
                        .clickable { showQrModal = true },
                    contentAlignment = Alignment.Center
                ) {
                    val qrBitmap1 = dynamicUpiQrBitmap
                    when {
                        qrBitmap1 != null -> Image(
                            bitmap = qrBitmap1.asImageBitmap(),
                            contentDescription = "Scan to pay ${profile?.upiHandle}",
                            modifier = Modifier.fillMaxSize()
                        )
                        isUpiQrLoading -> CircularProgressIndicator(color = PrimaryGold)
                        else -> Icon(
                            Icons.Default.QrCode,
                            null,
                            modifier = Modifier.size(KhanaBookTheme.iconSize.hero),
                            tint = Color.LightGray
                        )
                    }
                }
                Text(
                    "Tap to Enlarge",
                    color = PrimaryGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = spacing.extraSmall)
                )
                if (!canGenerateAmountQr) {
                    Text(
                        when {
                            profile?.upiHandle.isNullOrBlank() -> "Set UPI ID in Payment Configuration"
                            !isAmountValid -> "Enter a valid UPI split amount"
                            upiPayableAmount > upiMaxAmount -> PaymentLimits.UPI_LIMIT_MESSAGE
                            else -> "Add items before scanning UPI QR"
                        },
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = spacing.extraSmall)
                    )
                }
                if (upiExceedsLimit) {
                    Text(
                        "Amount exceeds UPI limit (₹1,00,000). Auto-switched to split payment. UPI will cover up to ₹1L, remaining to be collected via cash.",
                        color = WarningYellow,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = spacing.extraSmall).fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(spacing.large))
            }

            if (showQrModal && dynamicUpiQrBitmap != null) {
                KhanaBookDialog(
                    onDismissRequest = { showQrModal = false },
                    title = "Scan to Pay",
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(Color.White, KhanaRadii.lg)
                                .padding(spacing.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = dynamicUpiQrBitmap!!.asImageBitmap(),
                                contentDescription = "Enlarged QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Text(
                            "UPI ID: ${profile?.upiHandle}",
                            color = TextGold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    },
                    actions = {
                        TextButton(onClick = { showQrModal = false }) {
                            Text("Close", color = PrimaryGold)
                        }
                    }
                )
            }

            if (!isUpiMode) {
                Spacer(modifier = Modifier.height(spacing.large))
                Icon(
                    Icons.Default.Payment,
                    null,
                    tint = PrimaryGold.copy(alpha = 0.3f),
                    modifier = Modifier.size(KhanaBookTheme.iconSize.heroCircle)
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    "Complete Payment",
                    color = TextLight,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(spacing.large))
            }


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(relocationRequester)
                    .padding(vertical = spacing.small)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.small),
                    colors = CardDefaults.cardColors(containerColor = CardBG),
                    shape = KhanaRadii.lg,
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (partialRecovery != null) "Remaining Amount" else "Payable Amount",
                                color = TextGold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                    "₹${"%.2f".format(payableNow.toDoubleOrNull() ?: 0.0)}",
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Select Payment Mode:",
                        color = TextLight,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (controlsLocked && resumedPendingBillId != null) {
                        Text(
                            text = "Reset / Change Mode",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        resumedPendingBillId?.let { id: Long ->
                                            viewModel.finalizeOnlineBill(id, PaymentStatus.FAILED, "Cancelled by user to change payment mode")
                                        }
                                        resumedPendingBillId = null
                                    }
                                }
                                .padding(vertical = spacing.extraSmall)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.small))
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(KhanaBookTheme.spacing.buttonHeightLarge)
                                        .background(BrownSelected, KhanaRadii.md)
                                        .border(1.dp, BorderGold)
                                        .clickable(enabled = !controlsLocked) { expanded = true }
                                        .padding(horizontal = spacing.medium),
                        contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedMode.displayLabel, color = PrimaryGold, style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Default.ArrowDropDown, null, tint = PrimaryGold)
                    }
                    DropdownMenu(
                            expanded = expanded && !controlsLocked,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(DarkBrown2)
                    ) {
                        selectableModes.forEach { mode ->
                            DropdownMenuItem(
                                    text = { Text(mode.displayLabel, color = TextLight) },
                                    onClick = {
                                        selectedMode = mode
                                        expanded = false
                                    }
                            )
                        }
                    }
                }

                if (selectableModes.isEmpty()) {
                    Text(
                        "No unused configured payment mode is available. Reset this attempt or configure another payment method.",
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = spacing.small)
                    )
                }


                if (isSplitMode) {
                    Spacer(modifier = Modifier.height(spacing.large))
                    val labels =
                            when (selectedMode) {
                                PaymentMode.PART_CASH_UPI -> "Cash Amount" to "UPI Amount"
                                PaymentMode.PART_CASH_POS -> "Cash Amount" to "POS Amount"
                                PaymentMode.PART_UPI_POS -> "UPI Amount" to "POS Amount"
                                else -> "" to ""
                            }

                    val p1Requester = remember { BringIntoViewRequester() }
                    val p2Requester = remember { BringIntoViewRequester() }

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                    ) {
                        Box(modifier = Modifier.weight(1f).bringIntoViewRequester(p1Requester)) {
                            ParchmentTextField(
                                    value = p1Text,
                                    onValueChange = { p1Text = it },
                                    label = labels.first,
                                    modifier = Modifier.onFocusChanged {
                                        if (it.isFocused) {
                                            scope.launch { p1Requester.bringIntoView() }
                                        }
                                    },
                                    isError = !isAmountValid,
                                    enabled = !controlsLocked,
                                    keyboardOptions =
                                            androidx.compose.foundation.text.KeyboardOptions(
                                                    keyboardType =
                                                            androidx.compose.ui.text.input.KeyboardType
                                                                    .Decimal
                                            )
                            )
                        }
                        Box(modifier = Modifier.weight(1f).bringIntoViewRequester(p2Requester)) {
                            ParchmentTextField(
                                    value = p2Text,
                                    onValueChange = { p2Text = it },
                                    label = labels.second,
                                    modifier = Modifier.onFocusChanged {
                                        if (it.isFocused) {
                                            scope.launch { p2Requester.bringIntoView() }
                                        }
                                    },
                                    isError = !isAmountValid,
                                    enabled = !controlsLocked,
                                    keyboardOptions =
                                            androidx.compose.foundation.text.KeyboardOptions(
                                                    keyboardType =
                                                            androidx.compose.ui.text.input.KeyboardType
                                                                    .Decimal
                                            )
                            )
                        }
                    }

                    if (!isAmountValid) {
                        Text(
                                "Sum must equal ${CurrencyUtils.formatPrice(payableNow)} (Current: ${CurrencyUtils.formatPrice(p1 + p2)})",
                                color = DangerRed,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = spacing.extraSmall).align(Alignment.Start)
                        )
                    }
                }
            }


            LaunchedEffect(resumePendingPayment) {
                if (!resumePendingPayment) return@LaunchedEffect
                val pendingBillId = viewModel.getLatestPendingOnlineBillId()
                if (pendingBillId == null) {
                    val txnNote = latestPaymentEvent?.txnId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { " Txn: $it." }
                        ?: ""
                    val statusNote = latestPaymentEvent?.status?.name
                        ?.let { " Return status: $it." }
                        ?: ""
                    viewModel.reportError(
                        "Payment return received, but no matching pending bill was found.$txnNote$statusNote Check the payment app or contact support before retrying."
                    )
                    onBackToMenu()
                    return@LaunchedEffect
                }
                if (!viewModel.restorePendingOnlineBill(pendingBillId)) {
                    viewModel.reportError("Unable to restore pending online payment.")
                    onBackToMenu()
                    return@LaunchedEffect
                }
                resumedPendingBillId = pendingBillId
            }
        }
    }
    }
}


@Composable
fun FailedStep(
        viewModel: BillingViewModel,
        onRetryPayment: () -> Unit,
        onNewBill: () -> Unit
) {
    val lastBill by viewModel.lastBill.collectAsStateWithLifecycle()
    val totalAmount = lastBill?.bill?.totalAmount?.toDoubleOrNull() ?: 0.0
    val orderDisplay = lastBill?.bill?.dailyOrderDisplay ?: "-"
    val spacing = KhanaBookTheme.spacing

    ScrollableCenteredLayout(
        bottomBar = {
            OutlinedButton(
                onClick = onNewBill,
                modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightLarge),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold),
                shape = KhanaRadii.lg
            ) {
                Icon(Icons.Default.Home, null, tint = PrimaryGold)
                Spacer(modifier = Modifier.width(spacing.small))
                Text("Back to Home", color = TextGold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    ) {
        // Error icon
        Box(
            modifier = Modifier
                .size(KhanaBookTheme.iconSize.hero)
                .background(DangerRed.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, DangerRed.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Payment Failed",
                tint = DangerRed,
                modifier = Modifier.size(KhanaBookTheme.iconSize.avatar)
            )
        }

        Spacer(modifier = Modifier.height(spacing.large))

        Text(
            "Payment Failed / Cancelled",
            color = DangerRed,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            "Order #$orderDisplay · ₹${"%.2f".format(totalAmount)}",
            color = TextGold,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            "The payment was not completed. The order has been recorded as failed.",
            color = TextLight.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = spacing.small)
        )
    }
}


private fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> SuccessGreen
        PaymentMode.UPI -> Brown500 
        PaymentMode.POS -> PrimaryGold
        else -> Brown500
    }
}
