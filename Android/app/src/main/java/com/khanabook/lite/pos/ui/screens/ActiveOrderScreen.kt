@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import com.khanabook.lite.pos.ui.screens.activeorder.*
import com.khanabook.lite.pos.ui.screens.newbill.VariantPickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import com.khanabook.lite.pos.domain.manager.QrCodeManager
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ActiveOrderScreen(
    draftBillId: Long,
    onBack: () -> Unit,
    onOrderCompleted: () -> Unit,
    billingViewModel: BillingViewModel = hiltViewModel(),
    menuViewModel: MenuViewModel = hiltViewModel()
) {
    var isLoaded by remember { mutableStateOf(false) }
    var showVariantPickerFor by remember { mutableStateOf<com.khanabook.lite.pos.data.local.entity.MenuItemEntity?>(null) }
    var showItemNoteFor by remember { mutableStateOf<BillingViewModel.CartItem?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing

    LaunchedEffect(draftBillId) {
        billingViewModel.loadDraftOrderForEditing(draftBillId) {
            isLoaded = true
        }
    }

    val cartItems by billingViewModel.cartItems.collectAsStateWithLifecycle()
    val summary by billingViewModel.billSummary.collectAsStateWithLifecycle()
    val customerName by billingViewModel.customerName.collectAsStateWithLifecycle()
    val customerWhatsapp by billingViewModel.customerWhatsapp.collectAsStateWithLifecycle()
    val orderType by billingViewModel.orderType.collectAsStateWithLifecycle()
    val error by billingViewModel.error.collectAsStateWithLifecycle()
    val isLoading by billingViewModel.isLoading.collectAsStateWithLifecycle()
    val profile by billingViewModel.cachedProfile.collectAsStateWithLifecycle()

    LaunchedEffect(error) {
        error?.let { message ->
            KhanaToast.show(message, ToastKind.Error)
            billingViewModel.clearError()
        }
    }

    val total = summary.total.toDoubleOrNull() ?: 0.0
    val itemCount = cartItems.sumOf { it.quantity }

    val enabledModes = remember(profile) {
        profile?.let { PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
    }
    var selectedMode by remember(enabledModes) {
        mutableStateOf(if (enabledModes.contains(PaymentMode.CASH)) PaymentMode.CASH else enabledModes.firstOrNull() ?: PaymentMode.CASH)
    }
    var expanded by remember { mutableStateOf(false) }
    var p1Text by remember { mutableStateOf("") }
    var p2Text by remember { mutableStateOf("") }

    val isSplitMode = selectedMode == PaymentMode.PART_CASH_UPI ||
        selectedMode == PaymentMode.PART_CASH_POS ||
        selectedMode == PaymentMode.PART_UPI_POS

    val upiMaxAmount = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.toDouble()
    val isUpiMode = selectedMode == PaymentMode.UPI ||
        selectedMode == PaymentMode.PART_CASH_UPI ||
        selectedMode == PaymentMode.PART_UPI_POS

    LaunchedEffect(selectedMode, summary.total) {
        if (isSplitMode) {
            val split = when (selectedMode) {
                PaymentMode.PART_CASH_UPI -> if (total > upiMaxAmount) {
                    BillCalculator.splitCashUpiWithUpiCap(summary.total)
                } else BillCalculator.splitPartPayment(summary.total)
                PaymentMode.PART_UPI_POS -> if (total > upiMaxAmount) {
                    BillCalculator.splitUpiPosWithUpiCap(summary.total)
                } else BillCalculator.splitPartPayment(summary.total)
                else -> BillCalculator.splitPartPayment(summary.total)
            }
            p1Text = split.first
            p2Text = split.second
        }
    }

    val p1 = p1Text.toDoubleOrNull() ?: 0.0
    val p2 = p2Text.toDoubleOrNull() ?: 0.0
    val isAmountValid = if (isSplitMode) {
        BillCalculator.validatePartPayment(p1Text, p2Text, summary.total)
    } else true

    val upiPayableAmount = when (selectedMode) {
        PaymentMode.PART_CASH_UPI -> p2
        PaymentMode.PART_UPI_POS -> p1
        else -> total
    }

    val canGenerateAmountQr = isUpiMode && isAmountValid &&
        upiPayableAmount > 0.0 && upiPayableAmount <= upiMaxAmount &&
        !profile?.upiHandle.isNullOrBlank()

    val context = LocalContext.current
    val dynamicUpiQrBitmap by produceState<android.graphics.Bitmap?>(
        null, profile?.upiHandle, profile?.shopName, upiPayableAmount, canGenerateAmountQr
    ) {
        val handle = profile?.upiHandle
        value = if (canGenerateAmountQr && !handle.isNullOrBlank()) {
            val logo = loadShopLogoBlocking(context, profile?.logoUrl, profile?.logoPath)
            withContext(Dispatchers.Default) {
                QrCodeManager.generateUpiQrWithLogo(
                    handle, profile?.shopName ?: "RESTAURANT", upiPayableAmount, logo, 512
                )
            }
        } else null
    }

    Scaffold(
        containerColor = DarkBrown1,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Order — ${customerName.ifBlank { "Draft #$draftBillId" }}",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = PrimaryGold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
            )
        }
    ) { paddingValues ->
        if (!isLoaded) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGold)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.medium, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.smallMedium)
            ) {
                BillInfoHeader(
                    customerName = customerName,
                    customerWhatsapp = customerWhatsapp,
                    orderType = orderType,
                    itemCount = itemCount,
                    total = total
                )

                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))

                Text("Items ($itemCount)", color = PrimaryGold, style = MaterialTheme.typography.titleSmall)

                if (cartItems.isEmpty()) {
                    Text("No items. Add items from below.", color = TextGold.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                } else {
                    cartItems.forEach { cartItem ->
                        ActiveOrderItemCard(
                            cartItem = cartItem,
                            onIncrement = { billingViewModel.addToCart(cartItem.item, cartItem.variant) },
                            onDecrement = { billingViewModel.removeFromCart(cartItem.item, cartItem.variant) },
                            onRemove = {
                                repeat(cartItem.quantity) {
                                    billingViewModel.removeFromCart(cartItem.item, cartItem.variant)
                                }
                            },
                            onShowVariantPicker = {
                                if (cartItem.variant != null) {
                                    showVariantPickerFor = cartItem.item
                                }
                            },
                            onShowNote = { showItemNoteFor = cartItem }
                        )
                    }
                }

                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))

                Text("Add Items", color = PrimaryGold, style = MaterialTheme.typography.titleSmall)
                CompactMenuSection(
                    menuViewModel = menuViewModel,
                    billingViewModel = billingViewModel
                )

                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))

                BillSummaryDisplay(summary = summary)

                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))

                Text("Payment", color = PrimaryGold, style = MaterialTheme.typography.titleSmall)

                PaymentModeSelector(
                    enabledModes = enabledModes,
                    selectedMode = selectedMode,
                    onModeChange = { selectedMode = it },
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                )

                if (isSplitMode) {
                    val labels = when (selectedMode) {
                        PaymentMode.PART_CASH_UPI -> "Cash Amount" to "UPI Amount"
                        PaymentMode.PART_CASH_POS -> "Cash Amount" to "POS Amount"
                        PaymentMode.PART_UPI_POS -> "UPI Amount" to "POS Amount"
                        else -> "" to ""
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
                    ) {
                        ParchmentTextField(
                            value = p1Text,
                            onValueChange = { p1Text = it },
                            label = labels.first,
                            modifier = Modifier.weight(1f),
                            isError = !isAmountValid,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            )
                        )
                        ParchmentTextField(
                            value = p2Text,
                            onValueChange = { p2Text = it },
                            label = labels.second,
                            modifier = Modifier.weight(1f),
                            isError = !isAmountValid,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            )
                        )
                    }
                    if (!isAmountValid) {
                        Text(
                            "Sum must equal ${CurrencyUtils.formatPrice(summary.total)} (Current: ${CurrencyUtils.formatPrice(p1 + p2)})",
                            color = DangerRed,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (isUpiMode && canGenerateAmountQr) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(Color.White, KhanaRadii.lg)
                            .border(2.dp, PrimaryGold, KhanaRadii.lg)
                            .padding(spacing.small),
                        contentAlignment = Alignment.Center
                    ) {
                        val qrBitmap = dynamicUpiQrBitmap
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "UPI QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(color = PrimaryGold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.small))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            billingViewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                            val appended = billingViewModel.appendItemsToDraft(draftBillId)
                            if (!appended) return@launch
                            val settled = billingViewModel.settleDraftOrder(
                                billId = draftBillId,
                                paymentMode = selectedMode,
                                status = PaymentStatus.SUCCESS,
                                partAmount1 = p1Text,
                                partAmount2 = p2Text
                            )
                            if (settled) {
                                billingViewModel.clearActiveSession()
                                onOrderCompleted()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAmountValid) SuccessGreen else Color.Gray
                    ),
                    shape = KhanaRadii.lg,
                    enabled = isAmountValid && itemCount > 0 && !isLoading
                ) {
                    Text("Payment Successful — ${CurrencyUtils.formatPrice(total)}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            billingViewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                            val cancelled = billingViewModel.settleDraftOrder(
                                draftBillId,
                                selectedMode,
                                PaymentStatus.FAILED
                            )
                            if (cancelled) {
                                billingViewModel.clearActiveSession()
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    border = BorderStroke(1.dp, DangerRed),
                    shape = KhanaRadii.lg,
                    enabled = !isLoading
                ) {
                    Text("Payment Failed / Cancel", style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (billingViewModel.appendItemsToDraft(draftBillId)) {
                                KhanaToast.show("Draft updated", ToastKind.Success)
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
                    colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                    shape = KhanaRadii.lg,
                    enabled = itemCount > 0 && !isLoading
                ) {
                    Text("Update Items Only (Save Draft)", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }

                Spacer(modifier = Modifier.height(spacing.bottomListPadding))
            }
        }
    }

    showVariantPickerFor?.let { item ->
        val variants = menuViewModel.menuItems.collectAsStateWithLifecycle().value
            .find { it.menuItem.id == item.id }?.variants ?: emptyList()
        if (variants.isNotEmpty()) {
            VariantPickerDialog(
                itemName = item.name,
                variants = variants,
                onDismiss = { showVariantPickerFor = null },
                onSelect = { newVariant ->
                    val existingCartItem = cartItems.find { it.item.id == item.id && it.variant != null }
                    if (existingCartItem != null) {
                        billingViewModel.removeFromCart(existingCartItem.item, existingCartItem.variant)
                    }
                    billingViewModel.addToCart(item, newVariant)
                    showVariantPickerFor = null
                }
            )
        }
    }

    showItemNoteFor?.let { cartItem ->
        CartItemNoteDialog(
            initialNote = cartItem.note,
            itemName = cartItem.item.name,
            onDismiss = { showItemNoteFor = null },
            onSave = { note ->
                billingViewModel.updateCartItemNote(cartItem.item, cartItem.variant, note)
                showItemNoteFor = null
            }
        )
    }

    // Back-press guard during loading
    androidx.activity.compose.BackHandler(enabled = !isLoaded || isLoading) {
        if (!isLoading) onBack()
    }
}
