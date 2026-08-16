@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.manager.PaymentReturnManager
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.feedback.printFeedbackKind
import com.khanabook.lite.pos.ui.feedback.performMenuItemAdd
import com.khanabook.lite.pos.ui.feedback.rememberMenuFeedbackPreferences
import com.khanabook.lite.pos.ui.feedback.rememberMenuFeedbackSettings
import com.khanabook.lite.pos.ui.feedback.rememberMenuItemAddFeedback
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.khanabook.lite.pos.ui.screens.newbill.CustomerInfoStep
import com.khanabook.lite.pos.ui.screens.newbill.MenuSelectionStep
import com.khanabook.lite.pos.ui.screens.newbill.PaymentStep
import com.khanabook.lite.pos.ui.screens.newbill.SuccessStep
import com.khanabook.lite.pos.ui.screens.newbill.FailedStep

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val menuFeedbackPreferences = rememberMenuFeedbackPreferences()
    val menuFeedbackSettings by rememberMenuFeedbackSettings(menuFeedbackPreferences)
    val playMenuItemAddFeedback = rememberMenuItemAddFeedback(menuFeedbackSettings)
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
                KhanaToast.show("Payment confirmation in progress. Please wait.", ToastKind.Info)
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
            coroutineScope.launch { KhanaToast.show(it, ToastKind.Error) }
            billingViewModel.clearError()
        }
    }

    val navigateToHome: () -> Unit = {
        if (navController != null) {
            navController.navigate("main/0") {
                popUpTo("new_bill?resumePayment={resumePayment}&draftBillId={draftBillId}&targetStep={targetStep}") {
                    inclusive = true
                }
                launchSingleTop = true
            }
        } else {
            onBack()
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
                                    onShowMessage = { message ->
                                        coroutineScope.launch {
                                            KhanaToast.show(message, ToastKind.Warning)
                                        }
                                    },
                                    total = summary.total.toDoubleOrNull() ?: 0.0,
                                    itemCount = cartItems.sumOf { it.quantity },
                                    hideHeader = true,
                                    navController = navController,
                                    onReturnToTableList = returnToNewBillTables,
                                    onItemAddedFeedback = playMenuItemAddFeedback
                            )
                    3 ->
                            PaymentStep(
                                    billingViewModel,
                                    settingsViewModel,
                                    onBackToMenu = performBack,
                                    onComplete = { step = 4 },
                                    onFailed = { step = 5 },
                                    onFlowLockChange = { paymentFlowLocked = it },
                                    resumePendingPayment = shouldResumePendingPayment,
                                    onPayOnline = { billId, restaurantId ->
                                        navController?.navigate("easebuzz_payment/$billId/$restaurantId")
                                    }
                            )
                    4 ->
                            SuccessStep(
                                    billingViewModel,
                                    settingsViewModel,
                                    onDone = navigateToHome,
                                    onShowMessage = { msg ->
                                        coroutineScope.launch {
                                            KhanaToast.show(msg, printFeedbackKind(msg))
                                        }
                                    }
                            )
                    else ->
                            FailedStep(
                                    viewModel = billingViewModel,
                                    onRetryPayment = { step = 3 },
                                    onNewBill = navigateToHome
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
                shape = KhanaRadii.md
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
                modifier = Modifier.background(PrimaryGold, KhanaRadii.sm).height(32.dp)
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(KhanaBookTheme.iconSize.large)) {
                Icon(Icons.Default.Remove, null, tint = DarkBrown1, modifier = Modifier.size(16.dp))
            }
            Text("$quantity", color = DarkBrown1, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            IconButton(onClick = onAdd, modifier = Modifier.size(KhanaBookTheme.iconSize.large)) {
                Icon(Icons.Default.Add, null, tint = DarkBrown1, modifier = Modifier.size(16.dp))
            }
        }
    }
}

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
                        .size(KhanaBookTheme.iconSize.small)
                        .then(
                            if (icon == Icons.AutoMirrored.Filled.List) {
                                Modifier.offset(x = (-1).dp)
                            } else Modifier
                        )
                )
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .size(KhanaBookTheme.iconSize.xsmall)
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
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun menuTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGold,
    unfocusedBorderColor = BorderGold.copy(alpha = 0.4f),
    focusedTextColor = TextLight,
    unfocusedTextColor = TextLight,
    cursorColor = PrimaryGold,
    focusedContainerColor = DarkBrown2,
    unfocusedContainerColor = DarkBrown2,
    focusedLabelColor = PrimaryGold,
    unfocusedLabelColor = TextGold
)
