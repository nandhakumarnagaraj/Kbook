@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import android.speech.tts.TextToSpeech
import android.graphics.BitmapFactory
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.khanabook.lite.pos.domain.util.AppAssetStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.getInvoiceNumberDisplay
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import com.khanabook.lite.pos.domain.manager.PaymentRecoveryAssessment
import com.khanabook.lite.pos.domain.manager.PaymentReturnManager
import com.khanabook.lite.pos.domain.util.ConnectionStatus
import com.khanabook.lite.pos.domain.manager.QrCodeManager
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import kotlin.math.roundToLong
import java.util.Locale
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

internal enum class BillingBackAction {
    EXIT,
    STEP_ONE,
    STEP_TWO
}

internal fun resolveBillingBackAction(
    currentStep: Int,
    initialStep: Int,
    editingDraft: Boolean
): BillingBackAction =
    when {
        editingDraft && currentStep <= initialStep -> BillingBackAction.EXIT
        currentStep == 2 -> BillingBackAction.STEP_ONE
        currentStep == 3 -> BillingBackAction.STEP_TWO
        else -> BillingBackAction.EXIT
    }

@Composable
fun NewBillScreen(
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
        billingViewModel: BillingViewModel = hiltViewModel(),
        menuViewModel: MenuViewModel = hiltViewModel(),
        settingsViewModel: SettingsViewModel = hiltViewModel(),
    navController: androidx.navigation.NavController? = null,
    resumePendingPayment: Boolean = false,
    draftBillId: Long? = null,
    initialStep: Int = 1
) {
    var step by remember { mutableIntStateOf(if (resumePendingPayment) 3 else initialStep) }
    var paymentFlowLocked by remember { mutableStateOf(false) }

    LaunchedEffect(draftBillId) {
        if (draftBillId != null) {
            billingViewModel.loadDraftOrderForEditing(draftBillId) {
                step = initialStep
            }
        }
    }
    val shouldResumePendingPayment = resumePendingPayment
    val cartItems by billingViewModel.cartItems.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing

    var screenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { screenVisible = true }
    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )

    val summary by billingViewModel.billSummary.collectAsStateWithLifecycle()
    val error by billingViewModel.error.collectAsStateWithLifecycle()
    val isLoading by billingViewModel.isLoading.collectAsStateWithLifecycle()
    val activeDraftBills by billingViewModel.activeDraftBillsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val performBack: () -> Unit = {
        when (resolveBillingBackAction(step, initialStep, draftBillId != null)) {
            BillingBackAction.EXIT -> onBack()
            BillingBackAction.STEP_ONE -> step = 1
            BillingBackAction.STEP_TWO -> step = 2
        }
    }

    // Keep users inside the billing flow while an online payment is actively
    // being confirmed, otherwise online and offline paths can diverge.
    androidx.activity.compose.BackHandler(enabled = paymentFlowLocked || step > 1) {
        if (paymentFlowLocked) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Payment confirmation in progress. Please wait.")
            }
            return@BackHandler
        }
        performBack()
    }

    LaunchedEffect(draftBillId, resumePendingPayment) {
        if (draftBillId == null && !resumePendingPayment) {
            billingViewModel.resetForNewBill()
            billingViewModel.cancelPendingOnlineDrafts()
            PaymentReturnManager.clearLatestEvent()
            step = 1
        }
        if (resumePendingPayment) {
            step = 3
        }
    }

    LaunchedEffect(error) {
        error?.let {
            coroutineScope.launch { snackbarHostState.showSnackbar(it) }
            billingViewModel.clearError()
        }
    }

    LaunchedEffect(step) {
        if (step == 4) {
            coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.toast_order_placed)) }
        }
        if (step == 5) {
            coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.toast_payment_failed)) }
        }
    }

    val returnToCompletedOrders: () -> Unit = {
        if (navController != null) {
            val highlightedBillId = billingViewModel.lastBill.value?.bill?.id
            val route = if (highlightedBillId != null) {
                "main/3?source=ALL&highlightBillId=$highlightedBillId"
            } else {
                "main/3?source=ALL"
            }
            navController.navigate(route) {
                popUpTo("new_bill?resumePayment={resumePayment}&draftBillId={draftBillId}&targetStep={targetStep}") {
                    inclusive = true
                }
                launchSingleTop = true
            }
        } else {
            onBack()
        }
    }

    val returnToNewBillTables: () -> Unit = {
        if (navController != null) {
            val highlightedBillId = billingViewModel.lastBill.value?.bill?.id
            val route = if (highlightedBillId != null) {
                "main/3?source=ALL&highlightBillId=$highlightedBillId"
            } else {
                "main/3?source=ALL"
            }
            navController.navigate(route) {
                popUpTo("new_bill?resumePayment={resumePayment}&draftBillId={draftBillId}&targetStep={targetStep}") {
                    inclusive = true
                }
                launchSingleTop = true
            }
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = DarkBrown1,
        snackbarHost = { KhanaBookSnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(DarkBrown1)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "New Bill",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            enabled = !paymentFlowLocked,
                            onClick = {
                                if (paymentFlowLocked) return@IconButton
                                performBack()
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryGold)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
                )
                
                BillStepper(currentStep = step)
            }
        }
    ) { paddingValues ->
        AnimatedVisibility(
            visible = screenVisible,
            enter = enterSpec,
            exit = fadeOut(tween(200))
        ) {
        Box(modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .background(DarkBrown1)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { if (forward) it else -it } +
                        fadeIn(tween(300)) togetherWith
                    slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { if (forward) -it else it } +
                        fadeOut(tween(200))
                },
                label = "step_transition"
            ) { currentStep ->
                when (currentStep) {
                    1 ->
                            CustomerInfoStep(
                                    onNext = { name, whatsapp ->
                                        billingViewModel.setCustomerInfo(name, whatsapp)
                                        step = 2
                                    },
                                    onBack = onBack,
                                    hideHeader = true,
                                    billingViewModel = billingViewModel,
                                    activeDraftBills = activeDraftBills,
                                    onOpenDraftOrder = { billId, targetStep ->
                                        navController?.navigate("new_bill?draftBillId=$billId&targetStep=$targetStep")
                                    }
                            )
                    2 ->
                            MenuSelectionStep(
                                    billingViewModel,
                                    menuViewModel,
                                    onBack = performBack,
                                    onProceedToPayment = { step = 3 },
                                    total = summary.total.toDoubleOrNull() ?: 0.0,
                                    itemCount = cartItems.sumOf { it.quantity },
                                    hideHeader = true,
                                    navController = navController,
                                    onReturnToTableList = returnToNewBillTables
                            )
                    3 ->
                            PaymentStep(
                                    billingViewModel,
                                    settingsViewModel,
                                    onBackToMenu = performBack,
                                    onComplete = { step = 4 },
                                    onFailed = { step = 5 },
                                    onFlowLockChange = { paymentFlowLocked = it },
                                    resumePendingPayment = shouldResumePendingPayment
                            )
                    4 ->
                            SuccessStep(
                                    billingViewModel,
                                    settingsViewModel,
                                    onDone = returnToCompletedOrders,
                                    onShowMessage = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                    else ->
                            FailedStep(
                                    viewModel = billingViewModel,
                                    onRetryPayment = { step = 3 },
                                    onNewBill = returnToCompletedOrders
                            )
                }
            }

            KhanaBookLoadingOverlay(
                // Show loading on PaymentStep too when loading a draft bill,
                // preventing a flash of zero total while data loads.
                visible = isLoading && (step != 3 || draftBillId != null),
                type = LoadingType.SAVING
            )

        }
        } // end AnimatedVisibility
    }
}

@Composable
fun CustomerInfoStep(
    onNext: (String, String) -> Unit,
    onBack: () -> Unit,
    hideHeader: Boolean = false,
    billingViewModel: com.khanabook.lite.pos.ui.viewmodel.BillingViewModel? = null,
    activeDraftBills: List<BillEntity> = emptyList(),
    onOpenDraftOrder: (billId: Long, targetStep: Int) -> Unit = { _, _ -> }
) {
    var name by remember { mutableStateOf(billingViewModel?.customerName?.value ?: "") }
    var whatsapp by remember { mutableStateOf(billingViewModel?.customerWhatsapp?.value ?: "") }
    val spacing = KhanaBookTheme.spacing

    val recentCustomers by (billingViewModel?.recentCustomers ?: kotlinx.coroutines.flow.flowOf(emptyList<Pair<String,String>>())).collectAsStateWithLifecycle(emptyList())
    val recentDineInCustomers by (billingViewModel?.recentDineInCustomers ?: kotlinx.coroutines.flow.flowOf(emptyList<Pair<String,String>>())).collectAsStateWithLifecycle(emptyList())
    val currentOrderType by (billingViewModel?.orderType ?: kotlinx.coroutines.flow.flowOf("dine_in")).collectAsStateWithLifecycle("dine_in")
    var selectedOrderType by remember { mutableStateOf(if (currentOrderType == "takeaway") "takeaway" else "dine_in") }

    LaunchedEffect(Unit) {
        billingViewModel?.loadRecentCustomers()
        billingViewModel?.loadRecentDineInCustomers()
    }
    LaunchedEffect(currentOrderType) {
        selectedOrderType = if (currentOrderType == "takeaway") "takeaway" else "dine_in"
    }

    val showPhoneError = whatsapp.isNotEmpty() && !ValidationUtils.isValidPhone(whatsapp)
    val isNextEnabled = when (selectedOrderType) {
        "dine_in" -> ValidationUtils.isValidPhone(whatsapp)
        "takeaway" -> ValidationUtils.isValidPhone(whatsapp)
        else -> false
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.large)
    ) {
        if (!hideHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryGold
                    )
                }
                Column {
                    Text(
                            "New Bill",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.headlineMedium
                    )
                    Text("Customer Details & Order Type", color = TextGold, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }

        Text(
            "Order Type",
            color = TextGold,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(spacing.small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            OrderTypeButton(
                text = "Dine-In",
                isSelected = selectedOrderType == "dine_in",
                modifier = Modifier.weight(1f)
            ) {
                selectedOrderType = "dine_in"
                billingViewModel?.setOrderType("dine_in")
            }
            OrderTypeButton(
                text = "Takeaway",
                isSelected = selectedOrderType == "takeaway",
                modifier = Modifier.weight(1f)
            ) {
                selectedOrderType = "takeaway"
                billingViewModel?.setOrderType("takeaway")
            }
        }
        Spacer(modifier = Modifier.height(spacing.large))

        if (selectedOrderType == "active_order") {
            Text(
                "Active Orders",
                color = TextGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            if (activeDraftBills.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkBrown2,
                    border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "No active orders right now.",
                        modifier = Modifier.padding(spacing.medium),
                        color = TextGold.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    activeDraftBills.forEach { bill ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkBrown2,
                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 160.dp, max = 220.dp)
                                    .padding(spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                            ) {
                                Text(
                                    text = bill.customerName ?: "Table",
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CurrencyUtils.formatPrice(bill.totalAmount.toDoubleOrNull() ?: 0.0),
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                                    TextButton(
                                        onClick = { onOpenDraftOrder(bill.id, 2) },
                                        contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp)
                                    ) {
                                        Text("Add", color = VegGreen, style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = { onOpenDraftOrder(bill.id, 3) },
                                        contentPadding = PaddingValues(horizontal = spacing.small, vertical = 0.dp)
                                    ) {
                                        Text("Settle", color = PrimaryGold, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.large))
        }

        if (recentCustomers.isNotEmpty() && selectedOrderType == "takeaway") {
            Text(
                "Recent Customers",
                color = TextGold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                recentCustomers.forEach { customer ->
                    val phone = customer.first
                    val customerName = customer.second
                    Surface(
                        onClick = {
                            whatsapp = phone
                            name = customerName
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = DarkBrown2,
                        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = spacing.medium, vertical = spacing.small)) {
                            Text(
                                text = if (customerName.isNotBlank()) customerName else phone,
                                color = TextLight,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customerName.isNotBlank()) {
                                Text(phone, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.medium))
        }

        if (selectedOrderType == "dine_in") {
            if (recentDineInCustomers.isNotEmpty()) {
                Text(
                    "Recent Tables",
                    color = TextGold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    recentDineInCustomers.forEach { customer ->
                        val phone = customer.first
                        val customerName = customer.second
                        Surface(
                            onClick = {
                                whatsapp = phone
                                name = customerName
                                billingViewModel?.setCustomerInfo(customerName, phone)
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = DarkBrown2,
                            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = spacing.medium, vertical = spacing.small)) {
                                Text(
                                    text = if (customerName.isNotBlank()) customerName else phone,
                                    color = TextLight,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (customerName.isNotBlank() && phone.isNotBlank()) {
                                    Text(phone, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.medium))
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    billingViewModel?.setCustomerInfo(it, whatsapp)
                },
                label = { Text("Table name / Customer name") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryGold) }
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            OutlinedTextField(
                value = whatsapp,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                    whatsapp = filtered
                    billingViewModel?.setCustomerInfo(name, filtered)
                },
                label = { Text("WhatsApp Number *") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = VegGreen) },
                isError = showPhoneError,
                supportingText = {
                    if (showPhoneError) Text("Enter 10-digit number", color = DangerRed)
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            )
        } else if (selectedOrderType == "takeaway") {
            OutlinedTextField(
                value = whatsapp,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                    whatsapp = filtered
                    billingViewModel?.setCustomerInfo(name, filtered)
                },
                label = { Text("WhatsApp Number *") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = VegGreen) },
                isError = showPhoneError,
                supportingText = {
                    if (showPhoneError) Text("Enter 10-digit number", color = DangerRed)
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    billingViewModel?.setCustomerInfo(it, whatsapp)
                },
                label = { Text("Customer Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryGold) }
            )
        }

        if (selectedOrderType != "active_order") {
            Spacer(modifier = Modifier.height(spacing.huge))
            Button(
                onClick = { if (isNextEnabled) onNext(name, whatsapp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = if (isNextEnabled) PrimaryGold else Color.Gray
                        ),
                shape = RoundedCornerShape(12.dp),
                enabled = isNextEnabled
            ) {
                Text(
                        "Continue",
                        color = if (isNextEnabled) DarkBrown1 else Color.LightGray,
                        style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun OrderTypeButton(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
            modifier =
                    modifier.height(40.dp)
                            .background(
                                    if (isSelected) PrimaryGold else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                            )
                            .clickable { onClick() },
            contentAlignment = Alignment.Center
    ) {
        Text(
                text,
                color = if (isSelected) DarkBrown1 else TextGold,
                style = if (isSelected) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.labelMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MenuSelectionStep(
        billingViewModel: BillingViewModel,
        menuViewModel: MenuViewModel,
        onBack: () -> Unit,
        onProceedToPayment: () -> Unit,
        onShowMessage: (String) -> Unit = {},
        total: Double,
        itemCount: Int,
        hideHeader: Boolean = false,
        navController: NavController? = null,
        onReturnToTableList: () -> Unit = {}
) {
    val categories by menuViewModel.categories.collectAsStateWithLifecycle()
    val items by menuViewModel.menuItems.collectAsStateWithLifecycle()
    val searchResults by menuViewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by menuViewModel.searchQuery.collectAsStateWithLifecycle()
    val totalItemsCount by menuViewModel.totalItemsCount.collectAsStateWithLifecycle()
    val isCatalogLoaded by menuViewModel.isCatalogLoaded.collectAsStateWithLifecycle()
    val cartItems by billingViewModel.cartItems.collectAsStateWithLifecycle()
    val selectedCategoryId by menuViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val connectionStatus by billingViewModel.connectionStatus.collectAsStateWithLifecycle()
    val isOffline = connectionStatus == ConnectionStatus.Unavailable
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val displayItems = if (searchQuery.isNotBlank()) searchResults else items
    val hasNoMenuItems = isCatalogLoaded && totalItemsCount == 0 && searchQuery.isBlank()
    
    // Adaptive split-view: Categories on left, Cart on right for tablets
    val isWideScreen = layout.isWideListDetail
    val gridColumns = layout.menuGridColumns
    val scope = rememberCoroutineScope()
    val currentOrderType by billingViewModel.orderType.collectAsStateWithLifecycle()
    val profile by billingViewModel.cachedProfile.collectAsStateWithLifecycle()
    val paymentFlowMode = OrderPaymentFlowMode.fromDbValue(profile?.orderPaymentFlowMode)
    val canSaveTableOrder = currentOrderType == "dine_in" &&
        (paymentFlowMode == OrderPaymentFlowMode.PAY_AFTER_FOOD || billingViewModel.editingBillId != null)

    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            menuViewModel.selectCategory(categories.first().id)
        }
    }

    val derivedItemCount by remember {
        derivedStateOf { cartItems.sumOf { it.quantity } }
    }

    val proceedValidationMessage = if (hasNoMenuItems) {
        "Add menu items before creating a bill"
    } else {
        "Add at least one item to proceed"
    }

    val proceedOrValidate = {
        if (derivedItemCount > 0) {
            onProceedToPayment()
        } else {
            onShowMessage(proceedValidationMessage)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Main Menu Area
        Column(modifier = Modifier.weight(if (isWideScreen) 0.65f else 1f).fillMaxHeight()) {
            if (!hideHeader) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            null,
                            tint = PrimaryGold,
                            modifier = Modifier.clickable { onBack() }
                    )
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Text("New Bill", color = PrimaryGold, style = MaterialTheme.typography.titleLarge)
                }
            }

            // ── Offline sync banner ───────────────────────────────────────────
            // Visible only when the device has no network. Lets the cashier know
            // the bill is safely saved locally and will sync once back online.
            AnimatedVisibility(
                visible = isOffline,
                enter = expandVertically() + fadeIn(tween(300)),
                exit  = shrinkVertically() + fadeOut(tween(200))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WarningYellow.copy(alpha = 0.15f))
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = WarningYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Offline — bill will sync when back online",
                        color = WarningYellow,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { menuViewModel.setSearchQuery(it) },
                placeholder = { Text("Search items...", color = TextGold.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = PrimaryGold) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { menuViewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, null, tint = TextGold)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small),
                colors = menuTextFieldColors(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (searchQuery.isBlank() && categories.isNotEmpty()) {
                val selectedIndex =
                        categories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
                ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = PrimaryGold,
                        contentColor = DarkBrown1,
                        edgePadding = spacing.medium,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                        modifier =
                                                Modifier.tabIndicatorOffset(
                                                        tabPositions[selectedIndex]
                                                ),
                                        color = DarkBrown1
                                )
                            }
                        }
                ) {
                    categories.forEach { category ->
                        Tab(
                                selected = category.id == selectedCategoryId,
                                onClick = { menuViewModel.selectCategory(category.id) },
                                text = {
                                    Text(
                                            category.name,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (category.id == selectedCategoryId) FontWeight.Bold else FontWeight.Medium
                                            )
                                    )
                                },
                                selectedContentColor = DarkBrown1,
                                unselectedContentColor = DarkBrown1.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (!isCatalogLoaded && categories.isEmpty() && displayItems.isEmpty()) {
                SkeletonMenuScreen(modifier = Modifier.weight(1f))
            } else if (hasNoMenuItems) {
                NoMenuItemsEmptyState(modifier = Modifier.weight(1f))
            } else if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = TextGold.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(spacing.small))
                        Text(
                            if (searchQuery.isNotBlank()) "No items match \"$searchQuery\""
                            else "No items in this category",
                            color = TextGold.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.weight(1f).padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.smallMedium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
            ) {
                items(displayItems, key = { it.menuItem.id }) { menuWithVariants ->
                    val item = menuWithVariants.menuItem
                    val variants = menuWithVariants.variants
                    var showVariantPicker by remember { mutableStateOf(false) }
                    val itemAvailable = item.isAvailable

                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (itemAvailable) DarkBrown2 else DarkBrown2.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                    ) {
                        if (variants.isEmpty()) {
                            val cartItem =
                                    cartItems.find { it.item.id == item.id && it.variant == null }
                            Row(
                                    modifier = Modifier.padding(spacing.smallMedium),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                FoodTypeIcon(item.foodType)
                                Spacer(modifier = Modifier.width(spacing.smallMedium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                            item.name,
                                            color = if (itemAvailable) TextLight else TextLight.copy(alpha = 0.4f),
                                            style = MaterialTheme.typography.titleSmall
                                    )
                                    if (itemAvailable) {
                                        Text("₹${item.basePrice}", color = TextGold, style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        Text("Unavailable", color = ErrorPink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (itemAvailable) {
                                    QuantitySelector(
                                            quantity = cartItem?.quantity ?: 0,
                                            onAdd = { billingViewModel.addToCart(item) },
                                            onRemove = { billingViewModel.removeFromCart(item) }
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(spacing.smallMedium)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    FoodTypeIcon(item.foodType)
                                    Spacer(modifier = Modifier.width(spacing.smallMedium))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                item.name,
                                                color = if (itemAvailable) TextLight else TextLight.copy(alpha = 0.4f),
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                        if (!itemAvailable) {
                                            Text("Unavailable", color = ErrorPink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text(
                                                    "${variants.size} Variants",
                                                    color = PrimaryGold,
                                                    style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                if (itemAvailable) {
                                    Spacer(modifier = Modifier.height(spacing.small))
                                    HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(spacing.small))
                                    variants.forEach { variant ->
                                        val variantAvailable = variant.isAvailable
                                        val variantCartItem =
                                                cartItems.find {
                                                    it.item.id == item.id && it.variant?.id == variant.id
                                                }
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                    variant.variantName,
                                                    color = if (variantAvailable) TextGold else TextGold.copy(alpha = 0.35f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f)
                                            )
                                            if (variantAvailable) {
                                                Text(
                                                        CurrencyUtils.formatPrice(variant.price),
                                                        color = PrimaryGold,
                                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                        modifier = Modifier.padding(end = spacing.smallMedium)
                                                )
                                                QuantitySelector(
                                                        quantity = variantCartItem?.quantity ?: 0,
                                                        onAdd = { billingViewModel.addToCart(item, variant) },
                                                        onRemove = { billingViewModel.removeFromCart(item, variant) }
                                                )
                                            } else {
                                                Text("Unavailable", color = ErrorPink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showVariantPicker) {
                        VariantPickerDialog(
                                itemName = item.name,
                                variants = variants,
                                onDismiss = { showVariantPicker = false },
                                onSelect = { variant ->
                                    billingViewModel.addToCart(item, variant)
                                    showVariantPicker = false
                                }
                        )
                    }
                }
                
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(gridColumns) }) {
                    Spacer(modifier = Modifier.height(if (isWideScreen) spacing.medium else spacing.bottomListPadding))
                }
            }

            if (!isWideScreen) {
                // Bottom Floating Cart Card for Phones
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGold),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.smallMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    "$derivedItemCount Items Added",
                                    color = DarkBrown1,
                                    style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                    CurrencyUtils.formatPrice(total),
                                    color = DarkBrown1,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (canSaveTableOrder) {
                                if (billingViewModel.editingBillId != null) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                if (billingViewModel.appendItemsToDraft(billingViewModel.editingBillId!!)) {
                                                    if (navController != null) {
                                                        onReturnToTableList()
                                                    } else {
                                                        onBack()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.smallMedium),
                                        enabled = derivedItemCount > 0
                                    ) {
                                        Text(
                                            "Update Table",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                if (billingViewModel.saveDraftOrder(billingViewModel.customerName.value)) {
                                                    if (navController != null) {
                                                        onReturnToTableList()
                                                    } else {
                                                        onBack()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.smallMedium),
                                        enabled = derivedItemCount > 0
                                    ) {
                                        Text(
                                            "Save Table",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp)
                                        )
                                    }
                                }
                            }

                            if (!canSaveTableOrder) {
                                Button(
                                        onClick = onProceedToPayment,
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkBrown1),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = spacing.large, vertical = spacing.smallMedium),
                                        enabled = derivedItemCount > 0
                                    ) {
                                    Text(
                                            if (billingViewModel.editingBillId != null) "Settle" else "Proceed",
                                            color = PrimaryGold,
                                            style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Side Cart Area for Wide Screens
        if (isWideScreen) {
            Surface(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight(),
                color = DarkBrown2,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Text(
                        "Order Summary", 
                        color = PrimaryGold, 
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = spacing.bottomListPadding)
                    ) {
                        items(cartItems) { cartItem ->
                            var showNoteDialog by remember { mutableStateOf(false) }
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cartItem.item.name, color = TextLight, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (cartItem.variant != null) {
                                            Text(cartItem.variant.variantName, color = TextGold, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (cartItem.note.isNotBlank()) {
                                            Text("Note: ${cartItem.note}", color = PrimaryGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Text(
                                        "${cartItem.quantity} x ${CurrencyUtils.formatPrice(cartItem.variant?.price ?: cartItem.item.basePrice)}",
                                        color = TextLight,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    IconButton(onClick = { showNoteDialog = true }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            if (cartItem.note.isNotBlank()) Icons.Default.EditNote else Icons.AutoMirrored.Filled.NoteAdd,
                                            contentDescription = "Add note",
                                            tint = if (cartItem.note.isNotBlank()) PrimaryGold else TextGold.copy(alpha = 0.5f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = BorderGold.copy(alpha = 0.1f))
                            }
                            if (showNoteDialog) {
                                CartItemNoteDialog(
                                    initialNote = cartItem.note,
                                    itemName = cartItem.item.name,
                                    onDismiss = { showNoteDialog = false },
                                    onSave = { note ->
                                        billingViewModel.updateCartItemNote(cartItem.item, cartItem.variant, note)
                                        showNoteDialog = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(spacing.medium))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkBrown1),
                        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(spacing.medium)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Amount", color = TextGold, style = MaterialTheme.typography.bodyMedium)
                                Text(CurrencyUtils.formatPrice(total), color = PrimaryGold, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(spacing.medium))
                            if (canSaveTableOrder) {
                                if (billingViewModel.editingBillId != null) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                if (billingViewModel.appendItemsToDraft(billingViewModel.editingBillId!!)) {
                                                    if (navController != null) {
                                                        onReturnToTableList()
                                                    } else {
                                                        onBack()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = derivedItemCount > 0
                                    ) {
                                        Text("Update Table (Send KOT)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(modifier = Modifier.height(spacing.small))
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                if (billingViewModel.saveDraftOrder(billingViewModel.customerName.value)) {
                                                    if (navController != null) {
                                                        onReturnToTableList()
                                                    } else {
                                                        onBack()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = derivedItemCount > 0
                                    ) {
                                        Text("Save Table (Send KOT)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(modifier = Modifier.height(spacing.small))
                                }
                            }

                            if (!canSaveTableOrder) {
                                Button(
                                    onClick = onProceedToPayment,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = derivedItemCount > 0
                                ) {
                                    Text(
                                        if (billingViewModel.editingBillId != null) "Proceed to Settle" else "Proceed to Payment",
                                        color = DarkBrown1,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            if (derivedItemCount == 0) {
                                Text(
                                    "Add items from the menu to proceed",
                                    color = TextGold.copy(alpha = 0.45f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemNoteDialog(initialNote: String, itemName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var noteText by remember { mutableStateOf(initialNote) }
    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = "Note for $itemName",
        content = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("e.g. No onions, extra spicy...", color = TextGold.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                maxLines = 3,
                shape = RoundedCornerShape(8.dp)
            )
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextGold, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { onSave(noteText.trim()) }) {
            Text("Save", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun QuantitySelector(quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    if (quantity == 0) {
        OutlinedButton(
                onClick = onAdd,
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold),
                contentPadding = PaddingValues(horizontal = spacing.smallMedium)
        ) { Text("Add", style = MaterialTheme.typography.labelMedium) }
    } else {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.background(PrimaryGold, RoundedCornerShape(4.dp)).height(32.dp)
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Remove, null, tint = DarkBrown1, modifier = Modifier.size(16.dp))
            }
            Text("$quantity", color = DarkBrown1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, null, tint = DarkBrown1, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PaymentStep(
    viewModel: BillingViewModel,
    settingsViewModel: SettingsViewModel,
    onBackToMenu: () -> Unit,
    onComplete: () -> Unit,
    onFailed: () -> Unit = {},
    onFlowLockChange: (Boolean) -> Unit = {},
    resumePendingPayment: Boolean = false
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

    LaunchedEffect(latestPaymentEvent) {
        latestPaymentEvent?.let { event ->
            // SECURITY (KB-002): only accept payment return events that match the
            // in-flight pending bill. A spoofed deep link from another app/webpage
            // cannot mark an arbitrary bill as paid because:
            // 1. resumedPendingBillId must be non-null (we have an active payment sheet)
            // 2. The gateway status is stored as "deep_link_hint" — the actual bill
            //    finalization still requires the user to tap Confirm.
            if (resumedPendingBillId != null) {
                viewModel.setGatewayResult(event.txnId, event.status.name)
            }
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
    val paymentAttemptReady =
        recoveryConflict == null &&
            selectableModes.isNotEmpty() &&
            (!requiresOnlineAttempt || resumedPendingBillId != null)

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

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
            modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.large)
                    .imePadding()
                    .navigationBarsPadding(),
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
                shape = RoundedCornerShape(12.dp)
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
                    Modifier.size(200.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(2.dp, PrimaryGold, RoundedCornerShape(12.dp))
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
                                .background(Color.White, RoundedCornerShape(12.dp))
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
                    shape = RoundedCornerShape(12.dp),
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
                                        .height(56.dp)
                                        .background(BrownSelected, RoundedCornerShape(8.dp))
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

            Spacer(modifier = Modifier.height(spacing.extraLarge))
            Button(
                    onClick = {
                        if (!isAmountValid) return@Button
                        scope.launch {
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
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = if (isAmountValid) SuccessGreen else Color.Gray
                            ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isAmountValid && paymentAttemptReady
            ) {
                Text(
                        if (partialRecovery != null) {
                            "Confirm Remaining Payment"
                        } else {
                            "Payment Successful"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(spacing.smallMedium))
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
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = paymentAttemptReady
            ) {
                Text(
                    if (paymentRecovery !is PaymentRecoveryAssessment.Empty) {
                        "Keep Pending & Go Back"
                    } else {
                        "Payment Failed / Cancelled"
                    },
                    color = DangerRed,
                    style = MaterialTheme.typography.bodyMedium
                )
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

    Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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

        Spacer(modifier = Modifier.height(spacing.extraLarge))

        // "Back to Home" button
        OutlinedButton(
            onClick = onNewBill,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Home, null, tint = PrimaryGold)
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Back to Home", color = TextGold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SuccessStep(
        viewModel: BillingViewModel,
        settingsViewModel: SettingsViewModel,
        onDone: () -> Unit,
        onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lastBill by viewModel.lastBill.collectAsStateWithLifecycle()
    val printStatus by viewModel.printStatus.collectAsStateWithLifecycle()
    val receiptPrinting by viewModel.receiptPrinting.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val totalAmount = lastBill?.bill?.totalAmount?.toDoubleOrNull() ?: 0.0
    val scope = rememberCoroutineScope()
    var isSharingInvoice by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        try {
            engine = TextToSpeech(context.applicationContext) { status ->
                isTtsReady = status == TextToSpeech.SUCCESS
            }
            tts.value = engine
        } catch (e: Exception) {
            android.util.Log.e("NewBillScreen", "Failed to initialize TextToSpeech engine", e)
            isTtsReady = false
        }
        onDispose {
            try {
                engine?.stop()
                engine?.shutdown()
            } catch (e: Exception) {
                android.util.Log.e("NewBillScreen", "Error during TextToSpeech shutdown", e)
            }
            tts.value = null
            isTtsReady = false
        }
    }

    LaunchedEffect(isTtsReady, lastBill?.bill?.id) {
        if (!isTtsReady || lastBill == null) return@LaunchedEffect
        tts.value?.let { ttsEngine ->
            try {
                ttsEngine.language = Locale("en", "IN")
                ttsEngine.speak(
                    "Payment of ${formatAmountForSpeech(totalAmount)} received successfully.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "payment-success-${lastBill?.bill?.id}"
                )
            } catch (e: Exception) {
                android.util.Log.e("NewBillScreen", "Failed to speak total amount via TTS", e)
            }
        }
    }

    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        PaymentSuccessBadge()
        Text(
                "Payment Successful!",
                color = TextLight,
                style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(spacing.medium))

        // Receipt Summary Card
        KhanaBookCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.medium)
                .border(BorderStroke(1.dp, BorderGold.copy(alpha = 0.2f)), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBG.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TRANSACTION SUMMARY",
                    color = TextGold,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )
                Spacer(modifier = Modifier.height(spacing.medium))

                // Invoice No & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Invoice No:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = lastBill?.let { it.bill.getInvoiceNumberDisplay() } ?: "N/A",
                        color = TextLight,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment Mode:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Surface(
                        color = lastBill?.let { getPayModeColor(PaymentMode.fromDbValue(it.bill.paymentMode)) } ?: Color.Gray,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = lastBill?.let { PaymentMode.fromDbValue(it.bill.paymentMode).displayLabel } ?: "N/A",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                            modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Items Ordered:", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = lastBill?.let { "${it.items.size} item${if (it.items.size == 1) "" else "s"}" } ?: "N/A",
                        color = TextLight,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))
                HorizontalDivider(color = BorderGold.copy(alpha = 0.15f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(spacing.medium))

                Text(
                    text = "Amount Received",
                    color = TextGold,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    text = CurrencyUtils.formatPrice(totalAmount),
                    color = PrimaryGold,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        // Live Print Status or Connection Badge
        val liveStatus = printStatus ?: if (connectionStatus == ConnectionStatus.Unavailable) "Offline" else ""
        if (liveStatus.isNotEmpty()) {
            Surface(
                color = if (liveStatus.contains("failed") || liveStatus == "Offline") DangerRed.copy(alpha = 0.15f) else PrimaryGold.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (liveStatus.contains("failed") || liveStatus == "Offline") DangerRed.copy(alpha = 0.35f) else PrimaryGold.copy(alpha = 0.35f)),
                modifier = Modifier.padding(vertical = spacing.small)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (liveStatus.contains("failed") || liveStatus == "Offline") DangerRed else VegGreen,
                                CircleShape
                            )
                    )
                    Text(
                        text = liveStatus,
                        color = TextLight,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))
        LaunchedEffect(printStatus) {
            printStatus?.let { onShowMessage(it) }
        }
        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Button(
                onClick = {
                    val currentBill = lastBill ?: return@Button
                    scope.launch {
                        isSharingInvoice = true
                        try {
                            if (connectionStatus == ConnectionStatus.Unavailable) {
                                onShowMessage("Offline. Sharing invoice text by SMS.")
                                sendInvoiceViaSms(context, currentBill, profile)
                                return@launch
                            }

                            shareInstantInvoiceLink(context, currentBill, profile)
                        } finally {
                            isSharingInvoice = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhatsAppGreen,
                    contentColor = Color.White,
                    disabledContainerColor = WhatsAppGreen.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = lastBill != null && !isSharingInvoice
            ) {
                if (isSharingInvoice) {
                    KhanaInlineLoader(color = Color.White)
                    Spacer(modifier = Modifier.width(spacing.small))
                } else {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(iconSize.small))
                    Spacer(modifier = Modifier.width(spacing.small))
                }
                Text(
                    text = if (isSharingInvoice) "Preparing Link" else "Share Invoice",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            KhanaPrimaryButton(
                text = if (receiptPrinting) "Preparing Invoice" else "Print Invoice",
                onClick = { lastBill?.let { viewModel.printReceipt(it) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = lastBill?.let { it.bill.orderStatus != "cancelled" } == true && !receiptPrinting,
                isLoading = receiptPrinting,
                leadingIcon = Icons.Default.Receipt
            )

            Spacer(modifier = Modifier.height(spacing.small))

            KhanaSecondaryButton(
                text = "Back to Home",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Default.Home
            )
        }
    }
}

@Composable
private fun PaymentSuccessBadge() {
    val scale = remember { Animatable(0f) }
    val ripple1Scale = remember { Animatable(1f) }
    val ripple1Alpha = remember { Animatable(0.4f) }
    val ripple2Scale = remember { Animatable(1f) }
    val ripple2Alpha = remember { Animatable(0.4f) }
    val confettiProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animate checkmark circle scale with a bounce spring
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        // Animate ripple rings
        launch {
            ripple1Scale.animateTo(
                targetValue = 2.0f,
                animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
            )
        }
        launch {
            ripple1Alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
            )
        }
        // Delayed second ripple
        launch {
            kotlinx.coroutines.delay(200)
            launch {
                ripple2Scale.animateTo(
                    targetValue = 2.0f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
                )
            }
            launch {
                ripple2Alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutLinearInEasing)
                )
            }
        }
        // Confetti burst
        launch {
            kotlinx.coroutines.delay(300)
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Ripple 1
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer(
                    scaleX = ripple1Scale.value,
                    scaleY = ripple1Scale.value,
                    alpha = ripple1Alpha.value
                )
                .background(WhatsAppGreen.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, WhatsAppGreen.copy(alpha = 0.25f), CircleShape)
        )

        // Outer Ripple 2
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer(
                    scaleX = ripple2Scale.value,
                    scaleY = ripple2Scale.value,
                    alpha = ripple2Alpha.value
                )
                .background(WhatsAppGreen.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, WhatsAppGreen.copy(alpha = 0.25f), CircleShape)
        )

        // Main Checkmark Circle
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value
                )
                .background(WhatsAppGreen, CircleShape)
                .border(4.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        // Confetti Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val center = this.center
            val progress = confettiProgress.value
            if (progress > 0f && progress < 1f) {
                val numParticles = 16
                val maxRadius = 140.dp.toPx()
                val particleColors = listOf(
                    Color(0xFF4285F4), // Google Blue
                    Color(0xFFEA4335), // Google Red
                    Color(0xFFFBBC05), // Google Yellow
                    Color(0xFF34A853), // Google Green
                    Color(0xFFFF007F)  // Pink
                )
                for (i in 0 until numParticles) {
                    val angle = (i * 360f / numParticles) * (Math.PI / 180f)
                    val distance = maxRadius * progress
                    val x = center.x + (Math.cos(angle) * distance).toFloat()
                    val y = center.y + (Math.sin(angle) * distance).toFloat()
                    val color = particleColors[i % particleColors.size]
                    val size = 6.dp.toPx() * (1f - progress)
                    
                    // Draw alternating stars/squares/circles
                    when (i % 3) {
                        0 -> drawCircle(color = color, radius = size / 2, center = androidx.compose.ui.geometry.Offset(x, y))
                        1 -> drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(x - size/2, y - size/2), size = androidx.compose.ui.geometry.Size(size, size))
                        else -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(x, y - size/2)
                                lineTo(x + size/4, y - size/4)
                                lineTo(x + size/2, y)
                                lineTo(x + size/4, y + size/4)
                                lineTo(x, y + size/2)
                                lineTo(x - size/4, y + size/4)
                                lineTo(x - size/2, y)
                                lineTo(x - size/4, y - size/4)
                                close()
                            }
                            drawPath(path = path, color = color)
                        }
                    }
                }
            }
        }
    }
}

private fun formatAmountForSpeech(amount: Double): String {
    val totalPaise = (amount * 100).roundToLong().coerceAtLeast(0)
    val rupees = totalPaise / 100
    val paise = totalPaise % 100
    return if (paise == 0L) {
        "$rupees rupees"
    } else {
        "$rupees rupees and $paise paise"
    }
}

@Composable
fun VariantPickerDialog(
        itemName: String,
        variants: List<ItemVariantEntity>,
        onDismiss: () -> Unit,
        onSelect: (ItemVariantEntity) -> Unit
) {
    KhanaBookSelectionDialog(
            title = "Choose Variant",
            message = itemName,
            onDismissRequest = onDismiss,
        options = variants.map { variant ->
            SelectionDialogOption(
                value = variant,
                title = variant.variantName,
                subtitle = CurrencyUtils.formatPrice(variant.price.toString())
            )
        },
        onOptionSelected = onSelect
    )
}

@Composable
fun FoodTypeIcon(type: String) {
    val color = if (type == "veg") VegGreen else NonVegRed
    Box(
            modifier = Modifier.size(14.dp).border(1.dp, color).padding(2.dp),
            contentAlignment = Alignment.Center
    ) { Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(100.dp))) }
}

@Composable
private fun menuTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGold.copy(alpha = 0.3f),
                focusedBorderColor = PrimaryGold,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
        )

@Composable
fun BillStepper(currentStep: Int) {
    val spacing = KhanaBookTheme.spacing
    val resultLabel = when (currentStep) {
        4 -> "Success"
        5 -> "Failed"
        else -> "Result"
    }
    val resultIcon = when (currentStep) {
        4 -> Icons.Default.CheckCircle
        5 -> Icons.Default.Cancel
        else -> Icons.Default.Flag
    }
    val resultStepActive = currentStep >= 4
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.medium, start = spacing.extraLarge, end = spacing.extraLarge),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Center
    ) {
        StepItem(
            icon = Icons.Default.Person,
            label = "Customer",
            isActive = currentStep >= 1,
            isCompleted = currentStep > 1,
            showEndConnector = true,
            endConnectorCompleted = currentStep > 1,
            modifier = Modifier.weight(1f)
        )
        StepItem(
            icon = Icons.AutoMirrored.Filled.List,
            label = "Menu",
            isActive = currentStep >= 2,
            isCompleted = currentStep > 2,
            showStartConnector = true,
            startConnectorCompleted = currentStep > 1,
            showEndConnector = true,
            endConnectorCompleted = currentStep > 2,
            modifier = Modifier.weight(1f)
        )
        StepItem(
            icon = Icons.Default.Payments,
            label = "Payment",
            isActive = currentStep >= 3,
            isCompleted = currentStep > 3,
            showStartConnector = true,
            startConnectorCompleted = currentStep > 2,
            showEndConnector = true,
            endConnectorCompleted = resultStepActive,
            modifier = Modifier.weight(1f)
        )
        StepItem(
            icon = resultIcon,
            label = resultLabel,
            isActive = resultStepActive,
            isCompleted = currentStep == 4,
            showStartConnector = true,
            startConnectorCompleted = resultStepActive,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StepItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    showStartConnector: Boolean = false,
    startConnectorCompleted: Boolean = false,
    showEndConnector: Boolean = false,
    endConnectorCompleted: Boolean = false
) {
    val color = if (isActive) PrimaryGold else Color.Gray
    val containerColor = if (isActive) PrimaryGold.copy(alpha = 0.1f) else Color.Transparent

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showStartConnector) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(if (startConnectorCompleted) PrimaryGold else Color.Gray)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (isActive) containerColor else DarkBrown1, CircleShape)
                    .border(1.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .size(18.dp)
                        .then(
                            if (icon == Icons.AutoMirrored.Filled.List) {
                                Modifier.offset(x = (-1).dp)
                            } else Modifier
                        )
                )
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .background(VegGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            }

            if (showEndConnector) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(if (endConnectorCompleted) PrimaryGold else Color.Gray)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Text(label, color = color, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
    }
}

private suspend fun loadShopLogoBlocking(
    context: android.content.Context,
    logoUrl: String?,
    logoPath: String?
): android.graphics.Bitmap? {
    if (!logoUrl.isNullOrBlank()) {
        try {
            val request = ImageRequest.Builder(context)
                .data(logoUrl)
                .allowHardware(false)
                .size(128)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
            if (bitmap != null) return bitmap
        } catch (_: Exception) { }
    }
    return AppAssetStore.resolveAssetPath(logoPath)?.let { path ->
        try {
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) { null }
    }
}

@Composable
fun NoMenuItemsEmptyState(modifier: Modifier = Modifier) {
    val spacing = KhanaBookTheme.spacing
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.RestaurantMenu,
                contentDescription = null,
                tint = TextGold.copy(alpha = 0.35f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(spacing.smallMedium))
            Text(
                "No menu items added yet",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Text(
                "Add menu items before creating a bill.",
                color = TextGold.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
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

