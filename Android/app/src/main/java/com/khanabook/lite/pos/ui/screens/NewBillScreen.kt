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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun NewBillScreen(
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
        billingViewModel: BillingViewModel = hiltViewModel(),
        menuViewModel: MenuViewModel = hiltViewModel(),
        settingsViewModel: SettingsViewModel = hiltViewModel(),
    navController: androidx.navigation.NavController? = null,
    resumePendingPayment: Boolean = false
) {
    var step by rememberSaveable { mutableStateOf(1) }
    var paymentFlowLocked by rememberSaveable { mutableStateOf(false) }
    var hasInitialized by rememberSaveable { mutableStateOf(false) }
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Keep users inside the billing flow while an online payment is actively
    // being confirmed, otherwise online and offline paths can diverge.
    androidx.activity.compose.BackHandler(enabled = paymentFlowLocked || step > 1) {
        if (paymentFlowLocked) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Payment confirmation in progress. Please wait.")
            }
            return@BackHandler
        }
        when (step) {
            2 -> step = 1
            3 -> step = 2
            else -> onBack() // step 4 (success) and step 5 (failed): exit screen
        }
    }

    // Handle Easebuzz gateway return values
    val savedStateHandle = navController?.currentBackStackEntry?.savedStateHandle
    val gatewayTxnId = savedStateHandle?.get<String>("gatewayTxnId")
    val localBillId = savedStateHandle?.get<Long>("localBillId")
    LaunchedEffect(gatewayTxnId) {
        if (gatewayTxnId != null) {
            val billId = localBillId ?: billingViewModel.lastBill.value?.bill?.id
            if (billId != null) {
                savedStateHandle?.remove<String>("gatewayTxnId")
                savedStateHandle?.remove<Long>("localBillId")
                billingViewModel.setGatewayResult(gatewayTxnId, "success")
                if (billingViewModel.finalizeOnlineBill(billId, PaymentStatus.SUCCESS)) {
                    billingViewModel.clearGatewayResult()
                    step = 4
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasInitialized) {
            if (!resumePendingPayment) {
                billingViewModel.resetForNewBill()
                billingViewModel.cancelStaleOnlineDrafts()
                PaymentReturnManager.clearLatestEvent()
                step = 1
            }
            hasInitialized = true
        }
    }

    LaunchedEffect(shouldResumePendingPayment) {
        if (!shouldResumePendingPayment) return@LaunchedEffect
        val pendingBillId = billingViewModel.getLatestPendingOnlineBillId() ?: return@LaunchedEffect
        if (billingViewModel.restorePendingOnlineBill(pendingBillId)) {
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

    Scaffold(
        containerColor = MaterialTheme.kbBgPrimary,
        snackbarHost = { KhanaBookSnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.kbHeaderGradient
                    )
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable(enabled = !paymentFlowLocked) {
                                if (paymentFlowLocked) return@clickable
                                if (step == 1) onBack() else if (step == 2) step = 1 else if (step == 3) step = 2 else onBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White, CircleShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.khanabook_logo),
                                contentDescription = "Logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = "New Bill",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(36.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
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
            .background(MaterialTheme.kbBgPrimary)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val offsetSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    val floatSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                    slideInHorizontally(animationSpec = offsetSpring) { if (forward) it else -it } +
                        fadeIn(floatSpring) togetherWith
                    slideOutHorizontally(animationSpec = offsetSpring) { if (forward) -it else it } +
                        fadeOut(floatSpring)
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
                                    billingViewModel = billingViewModel
                            )
                    2 ->
                            MenuSelectionStep(
                                    billingViewModel,
                                    menuViewModel,
                                    onBack = { step = 1 },
                                    onProceedToPayment = { step = 3 },
                                    total = summary.total.toDoubleOrNull() ?: 0.0,
                                    itemCount = cartItems.sumOf { it.quantity },
                                    hideHeader = true,
                                    navController = navController
                            )
                    3 ->
                            PaymentStep(
                                    billingViewModel,
                                    settingsViewModel,
                                    onBackToMenu = { step = 2 },
                                    onComplete = { step = 4 },
                                    onFailed = { step = 5 },
                                    onFlowLockChange = { paymentFlowLocked = it },
                                    resumePendingPayment = shouldResumePendingPayment,
                                    navController = navController
                            )
                    4 ->
                            SuccessStep(
                                    billingViewModel,
                                    settingsViewModel,
                                    onDone = onBack,
                                    onShowMessage = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                    else ->
                            FailedStep(
                                    viewModel = billingViewModel,
                                    onRetryPayment = { step = 3 },
                                    onNewBill = onBack
                            )
                }
            }

            KhanaBookLoadingOverlay(
                visible = isLoading,
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
    billingViewModel: com.khanabook.lite.pos.ui.viewmodel.BillingViewModel? = null
) {
    // Restore from ViewModel so state survives AnimatedContent recreation when going back
    var name by remember { mutableStateOf(billingViewModel?.customerName?.value ?: "") }
    var whatsapp by remember { mutableStateOf(billingViewModel?.customerWhatsapp?.value ?: "") }
    val spacing = KhanaBookTheme.spacing

    val recentCustomers by (billingViewModel?.recentCustomers ?: kotlinx.coroutines.flow.flowOf(emptyList<Pair<String,String>>())).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) { billingViewModel?.loadRecentCustomers() }

    val isWhatsappValid = whatsapp.isEmpty() || ValidationUtils.isValidPhone(whatsapp)
    val isNextEnabled = true

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
                            tint = MaterialTheme.kbSecondary
                    )
                }
                Column {
                    Text(
                            "New Bill",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.headlineMedium
                    )
                    Text("Customer Details", color = MaterialTheme.kbTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }

        if (recentCustomers.isNotEmpty()) {
            Text(
                "RECENT CUSTOMERS",
                color = MaterialTheme.kbTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
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
                        color = MaterialTheme.kbBgCard,
                        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle)
                    ) {
                        Column(modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = spacing.medium, vertical = spacing.small)) {
                            Text(
                                text = if (customerName.isNotBlank()) customerName else phone,
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customerName.isNotBlank()) {
                                Text(
                                    phone,
                                    color = MaterialTheme.kbTextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.medium))
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
            Text(
                "CUSTOMER WHATSAPP (OPTIONAL)",
                color = MaterialTheme.kbTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
            KhanaBookInputField(
                value = whatsapp,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }.take(10)
                    whatsapp = filtered
                    billingViewModel?.setCustomerInfo(name, filtered)
                },
                placeholder = "Enter WhatsApp number",
                label = "",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = KbSuccess) },
                isError = false,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                )
            )
        }

        TextButton(
            onClick = { onNext("", "") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Skip — Walk-in Customer",
                color = MaterialTheme.kbSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(modifier = Modifier.height(spacing.small))

        Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
            Text(
                "CUSTOMER NAME (OPTIONAL)",
                color = MaterialTheme.kbTertiary,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
            KhanaBookInputField(
                value = name,
                onValueChange = {
                    name = it
                    billingViewModel?.setCustomerInfo(it, whatsapp)
                },
                placeholder = "Enter customer name",
                label = "",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.kbSecondary) }
            )
        }

        Spacer(modifier = Modifier.height(spacing.huge))
        Button(
            onClick = { if (isNextEnabled) onNext(name, whatsapp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isNextEnabled) KbBrandSaffron else MaterialTheme.kbBgSecondary,
                contentColor = Color.White,
                disabledContainerColor = MaterialTheme.kbBgSecondary,
                disabledContentColor = Color.White.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(10.dp),
            enabled = isNextEnabled
        ) {
            Text(
                "Continue",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun OrderTypeButton(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
            modifier =
                    modifier.height(40.dp)
                            .background(                                    if (isSelected) MaterialTheme.kbPrimary else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                            )
                            .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                    text,
                    color = if (isSelected) MaterialTheme.kbTextPrimary else MaterialTheme.kbTextSecondary,
                style = if (isSelected) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun MenuSelectionStep(
        billingViewModel: BillingViewModel,
        menuViewModel: MenuViewModel,
        onBack: () -> Unit,
        onProceedToPayment: () -> Unit,
        total: Double,
        itemCount: Int,
        hideHeader: Boolean = false,
        navController: NavController? = null
) {
    val categories by menuViewModel.categories.collectAsStateWithLifecycle()
    val items by menuViewModel.menuItems.collectAsStateWithLifecycle()
    val searchResults by menuViewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by menuViewModel.searchQuery.collectAsStateWithLifecycle()
    val cartItems by billingViewModel.cartItems.collectAsStateWithLifecycle()
    val selectedCategoryId by menuViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val connectionStatus by billingViewModel.connectionStatus.collectAsStateWithLifecycle()
    val isOffline = connectionStatus == ConnectionStatus.Unavailable
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val displayItems = if (searchQuery.isNotBlank()) searchResults else items
    
    val isWideScreen = layout.isWideListDetail
    val gridColumns = layout.menuGridColumns

    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            menuViewModel.selectCategory(categories.first().id)
        }
    }

    val derivedItemCount by remember {
        derivedStateOf { cartItems.sumOf { it.quantity } }
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            TabletCategoryRail(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                searchQuery = searchQuery,
                isOffline = isOffline,
                onCategorySelected = { menuViewModel.selectCategory(it) },
                onClearSearch = { menuViewModel.setSearchQuery("") },
                onSearchChange = { menuViewModel.setSearchQuery(it) },
                modifier = Modifier.weight(0.22f)
            )

            VerticalDivider(color = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.35f))

            Column(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight()
            ) {
                AnimatedVisibility(
                    visible = isOffline,
                    enter = expandVertically() + fadeIn(tween(300)),
                    exit = shrinkVertically() + fadeOut(tween(200))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KbWarning.copy(alpha = 0.12f))
                            .padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = KbWarning, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Offline - bill will sync when back online",
                            color = KbWarning,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (searchQuery.isBlank()) "Fast billing" else "Search results",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "Tap items to build the ticket" else "Filtered across the current menu",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = KbBrandSaffron.copy(alpha = KbOpacity.StatusBg),
                        border = BorderStroke(1.dp, KbBrandSaffron.copy(alpha = KbOpacity.StatusBorder))
                    ) {
                        Text(
                            text = "$derivedItemCount items",
                            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                            color = KbBrandSaffron,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                when {
                    categories.isEmpty() && displayItems.isEmpty() -> {
                        SkeletonMenuScreen(modifier = Modifier.weight(1f))
                    }
                    displayItems.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(spacing.small))
                                Text(
                                    if (searchQuery.isNotBlank()) "No items match \"$searchQuery\"" else "No items in this category",
                                    color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                start = spacing.medium,
                                end = spacing.medium,
                                bottom = spacing.medium,
                                top = spacing.small
                            ),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            items(displayItems, key = { it.menuItem.id }) { menuWithVariants ->
                                val item = menuWithVariants.menuItem
                                val variants = menuWithVariants.variants
                                val categoryName = categories.find { it.id == item.categoryId }?.name ?: ""
                                var showVariantPicker by remember { mutableStateOf(false) }
                                MenuItemGridCard(
                                    item = item,
                                    variants = variants,
                                    categoryName = categoryName,
                                    cartItems = cartItems,
                                    onAdd = { billingViewModel.addToCart(item) },
                                    onRemove = { billingViewModel.removeFromCart(item) },
                                    onOpenVariantPicker = { showVariantPicker = true }
                                )

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
                        }
                    }
                }
            }

            VerticalDivider(color = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.35f))

            TabletCartPanel(
                cartItems = cartItems,
                total = total,
                derivedItemCount = derivedItemCount,
                billingViewModel = billingViewModel,
                onProceedToPayment = onProceedToPayment,
                modifier = Modifier.weight(0.30f)
            )
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AnimatedVisibility(
                    visible = isOffline,
                    enter = expandVertically() + fadeIn(tween(300)),
                    exit = shrinkVertically() + fadeOut(tween(200))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KbWarning.copy(alpha = 0.15f))
                            .padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = KbWarning, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Offline - bill will sync when back online",
                            color = KbWarning,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                KhanaBookInputField(
                    value = searchQuery,
                    onValueChange = { menuViewModel.setSearchQuery(it) },
                    label = "Search",
                    placeholder = "Search items...",
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.kbTextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { menuViewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.kbTextSecondary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small)
                )

                if (searchQuery.isBlank() && categories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = spacing.small),
                        contentPadding = PaddingValues(horizontal = spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        items(categories, key = { it.id }) { category ->
                            val isSelected = category.id == selectedCategoryId
                            Surface(
                                onClick = { menuViewModel.selectCategory(category.id) },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) KbBrandSaffron else MaterialTheme.kbBgCard,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isSelected) KbBrandSaffron else MaterialTheme.kbOutlineSubtle
                                )
                            ) {
                                Text(
                                    category.name,
                                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = 6.dp),
                                    color = if (isSelected) Color.White else MaterialTheme.kbTextSecondary,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                if (categories.isEmpty() && displayItems.isEmpty()) {
                    SkeletonMenuScreen(modifier = Modifier.weight(1f))
                } else if (displayItems.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(spacing.small))
                            Text(
                                if (searchQuery.isNotBlank()) "No items match \"$searchQuery\"" else "No items in this category",
                                color = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = spacing.medium,
                            end = spacing.medium,
                            bottom = spacing.bottomListPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                    ) {
                        items(displayItems, key = { it.menuItem.id }) { menuWithVariants ->
                            val item = menuWithVariants.menuItem
                            val variants = menuWithVariants.variants
                            var showVariantPicker by remember { mutableStateOf(false) }
                            val itemAvailable = item.isAvailable
                            val categoryName = categories.find { it.id == item.categoryId }?.name ?: ""

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (itemAvailable) Color.White else Color.White.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.name,
                                            color = if (itemAvailable) MaterialTheme.kbTextPrimary else MaterialTheme.kbTextSecondary,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (categoryName.isNotBlank()) {
                                            Text(
                                                categoryName,
                                                color = MaterialTheme.kbTextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(spacing.small))

                                    if (itemAvailable) {
                                        if (variants.isEmpty()) {
                                            val cartItem = cartItems.find { it.item.id == item.id && it.variant == null }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                            CurrencyUtils.formatPrice(item.basePrice.toString()),
                                                    color = KbBrandSaffron,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                                QuantitySelector(
                                                    quantity = cartItem?.quantity ?: 0,
                                                    onAdd = { billingViewModel.addToCart(item) },
                                                    onRemove = { billingViewModel.removeFromCart(item) }
                                                )
                                            }
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    "${variants.size} var.",
                                                    color = MaterialTheme.kbTextSecondary,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                OutlinedButton(
                                                    onClick = { showVariantPicker = true },
                                                    modifier = Modifier.height(34.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = KbBrandSaffron),
                                                    border = BorderStroke(1.dp, KbBrandSaffron),
                                                    contentPadding = PaddingValues(horizontal = spacing.small)
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            "Unavailable",
                                            color = KbBrandRed.copy(alpha = KbOpacity.Muted),
                                            style = MaterialTheme.typography.labelSmall
                                        )
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
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    shape = KbShape.Medium,
                    color = MaterialTheme.kbBgCard,
                    tonalElevation = KbElevation.Medium
                ) {
                    Column(modifier = Modifier.padding(spacing.smallMedium)) {
                        if (cartItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                            ) {
                                cartItems.forEach { cartItem ->
                                    val itemUnitPrice = (cartItem.variant?.price ?: cartItem.item.basePrice).toDoubleOrNull() ?: 0.0
                                    val lineTotal = itemUnitPrice * cartItem.quantity
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = spacing.hairline),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                cartItem.item.name,
                                                color = MaterialTheme.kbTextPrimary,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (cartItem.variant != null) {
                                                Text(
                                                    cartItem.variant.variantName,
                                                    color = MaterialTheme.kbTextSecondary,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (cartItem.variant != null) {
                                                        billingViewModel.removeFromCart(cartItem.item, cartItem.variant)
                                                    } else {
                                                        billingViewModel.removeFromCart(cartItem.item)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp),
                                                colors = IconButtonDefaults.iconButtonColors(contentColor = KbBrandSaffron)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                                            }
                                            Text(
                                                "${cartItem.quantity}",
                                                color = MaterialTheme.kbTextPrimary,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (cartItem.variant != null) {
                                                        billingViewModel.addToCart(cartItem.item, cartItem.variant)
                                                    } else {
                                                        billingViewModel.addToCart(cartItem.item)
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp),
                                                colors = IconButtonDefaults.iconButtonColors(contentColor = KbBrandSaffron)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                                            }
                                            Text(
                                                CurrencyUtils.formatPrice(lineTotal),
                                                color = MaterialTheme.kbTextPrimary,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                modifier = Modifier.widthIn(min = 56.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        color = MaterialTheme.kbOutlineSubtle,
                                        thickness = spacing.hairline
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.small))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$derivedItemCount items",
                                color = MaterialTheme.kbTextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                CurrencyUtils.formatPrice(total),
                                color = KbBrandSaffron,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.small))
                        Button(
                            onClick = onProceedToPayment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(KbButtonSize.HeightLarge),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KbBrandSaffron,
                                disabledContainerColor = KbBrandSaffron.copy(alpha = KbOpacity.Disabled)
                            ),
                            shape = KbShape.Small,
                            enabled = derivedItemCount > 0
                        ) {
                            Text(
                                if (derivedItemCount > 0) "Place Order - ${CurrencyUtils.formatPrice(total)}" else "Place Order",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletCategoryRail(
    categories: List<com.khanabook.lite.pos.data.local.entity.CategoryEntity>,
    selectedCategoryId: Long?,
    searchQuery: String,
    isOffline: Boolean,
    onCategorySelected: (Long) -> Unit,
    onClearSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.kbBgSecondary)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Text(
                text = "Categories",
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "Fast touch access",
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        KhanaBookInputField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = "Search",
            placeholder = "Search items...",
            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.kbTextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.kbTextSecondary)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (isOffline) {
            Surface(
                shape = KbShape.Small,
                color = KbWarning.copy(alpha = KbOpacity.StatusBg),
                border = BorderStroke(1.dp, KbWarning.copy(alpha = KbOpacity.StatusBorder))
            ) {
                Row(
                    modifier = Modifier.padding(spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    Icon(Icons.Default.CloudOff, null, tint = KbWarning, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Offline sync pending",
                        color = KbWarning,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            contentPadding = PaddingValues(bottom = spacing.small)
        ) {
            items(categories, key = { it.id }) { category ->
                val isSelected = category.id == selectedCategoryId
                Surface(
                    onClick = { onCategorySelected(category.id) },
                    shape = KbShape.Small,
                    color = if (isSelected) KbBrandSaffron.copy(alpha = 0.12f) else MaterialTheme.kbBgCard,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) KbBrandSaffron.copy(alpha = 0.35f) else MaterialTheme.kbOutlineSubtle
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = category.name,
                            color = if (isSelected) KbBrandSaffron else MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isSelected) "Active" else "Tap to filter",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemGridCard(
    item: com.khanabook.lite.pos.data.local.entity.MenuItemEntity,
    variants: List<com.khanabook.lite.pos.data.local.entity.ItemVariantEntity>,
    categoryName: String,
    cartItems: List<BillingViewModel.CartItem>,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onOpenVariantPicker: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val itemAvailable = item.isAvailable
    val cartItem = cartItems.find { it.item.id == item.id && it.variant == null }
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        shape = KbShape.Small
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (itemAvailable) KbSuccess.copy(alpha = KbOpacity.StatusBg) else KbError.copy(alpha = KbOpacity.StatusBg),
                    border = BorderStroke(
                        1.dp,
                        if (itemAvailable) KbSuccess.copy(alpha = KbOpacity.StatusBorder) else KbError.copy(alpha = KbOpacity.StatusBorder)
                    )
                ) {
                    Text(
                        text = if (itemAvailable) "Available" else "Hidden",
                        modifier = Modifier.padding(horizontal = spacing.small, vertical = 4.dp),
                        color = if (itemAvailable) KbSuccess else KbError,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                if (categoryName.isNotBlank()) {
                    Text(
                        text = categoryName,
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Text(
                text = item.name,
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.description?.isNotBlank() == true) {
                Text(
                    text = item.description,
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (variants.isEmpty()) CurrencyUtils.formatPrice(item.basePrice.toString()) else "${variants.size} variants",
                        color = KbBrandSaffron,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (cartItem != null) {
                        Text(
                            text = "${cartItem.quantity} in cart",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (itemAvailable) {
                    if (variants.isEmpty()) {
                        QuantitySelector(
                            quantity = cartItem?.quantity ?: 0,
                            onAdd = onAdd,
                            onRemove = onRemove
                        )
                    } else {
                        OutlinedButton(
                            onClick = onOpenVariantPicker,
                            modifier = Modifier.height(KbButtonSize.HeightSmall),
                            shape = KbShape.ExtraSmall,
                            border = BorderStroke(1.dp, KbBrandSaffron),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KbBrandSaffron),
                            contentPadding = PaddingValues(horizontal = spacing.small)
                        ) {
                            Text("Choose", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletCartPanel(
    cartItems: List<BillingViewModel.CartItem>,
    total: Double,
    derivedItemCount: Int,
    billingViewModel: BillingViewModel,
    onProceedToPayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.kbBgSecondary,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Order Summary",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "Persistent cart and payment lane",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                items(cartItems, key = { "${it.item.id}_${it.variant?.id ?: 0}" }) { cartItem ->
                    var showNoteDialog by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.animateItem()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    cartItem.item.name,
                                    color = MaterialTheme.kbTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (cartItem.variant != null) {
                                    Text(
                                        cartItem.variant.variantName,
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (cartItem.note.isNotBlank()) {
                                    Text(
                                        "Note: ${cartItem.note}",
                                        color = MaterialTheme.kbPrimary.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                "${cartItem.quantity} x ${CurrencyUtils.formatPrice((cartItem.variant?.price ?: cartItem.item.basePrice).toString())}",
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                            IconButton(onClick = { showNoteDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (cartItem.note.isNotBlank()) Icons.Default.EditNote else Icons.AutoMirrored.Filled.NoteAdd,
                                    contentDescription = "Add note",
                                    tint = if (cartItem.note.isNotBlank()) MaterialTheme.kbSecondary else MaterialTheme.kbTextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.1f))
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Amount", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(CurrencyUtils.formatPrice(total), color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = KbBrandSaffron.copy(alpha = KbOpacity.StatusBg),
                        border = BorderStroke(1.dp, KbBrandSaffron.copy(alpha = KbOpacity.StatusBorder))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.small, vertical = spacing.extraSmall),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Items", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.labelMedium)
                            Text("$derivedItemCount", color = KbBrandSaffron, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Button(
                        onClick = onProceedToPayment,
                        modifier = Modifier.fillMaxWidth().height(KbButtonSize.HeightLarge),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = derivedItemCount > 0
                    ) {
                        Text("Proceed to Payment", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    if (derivedItemCount == 0) {
                        Text(
                            "Add items from the menu to proceed",
                            color = MaterialTheme.kbTextSecondary.copy(alpha = 0.45f),
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

@Composable
private fun CartItemNoteDialog(initialNote: String, itemName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var noteText by remember { mutableStateOf(initialNote) }
    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = "Note for $itemName",
        content = {
            KhanaBookInputField(
                value = noteText,
                onValueChange = { noteText = it },
                label = "Note",
                placeholder = "e.g. No onions, extra spicy...",
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = { onSave(noteText.trim()) }) {
            Text("Save", color = MaterialTheme.kbPrimary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun QuantitySelector(quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    if (quantity == 0) {
        // Zero state: saffron filled square + button
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(KbBrandSaffron, RoundedCornerShape(8.dp))
                .clickable {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onAdd()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    } else {
        // Active state: [dark indigo –] [count] [saffron +]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Minus button — dark warm saffron
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.kbPrimaryBold, RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onRemove()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            // Count
            Text(
                text = "$quantity",
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.widthIn(min = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            // Plus button — saffron
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(KbBrandSaffron, RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onAdd()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


@Composable
fun PaymentStep(
    viewModel: BillingViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onBackToMenu: () -> Unit = {},
    onComplete: () -> Unit = {},
    onFailed: () -> Unit = {},
    onFlowLockChange: (Boolean) -> Unit = {},
    resumePendingPayment: Boolean = false,
    navController: NavController? = null
) {
    val summary by viewModel.billSummary.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val spacing = KhanaBookTheme.spacing
    val enabledModes =
            remember(profile) {
                profile?.let { PaymentModeManager.getEnabledModes(it) } ?: listOf(PaymentMode.CASH)
            }
    var selectedMode by remember { mutableStateOf(PaymentMode.UPI) }
    var expanded by remember { mutableStateOf(false) }
    var showQrModal by remember { mutableStateOf(false) }

    var p1Text by remember { mutableStateOf("") }
    var p2Text by remember { mutableStateOf("") }

    
    LaunchedEffect(enabledModes) {
        if (enabledModes.isNotEmpty()) {
            selectedMode = when {
                enabledModes.contains(PaymentMode.UPI) -> PaymentMode.UPI
                else -> enabledModes.first()
            }
        }
    }

    val isUpiMode =
            selectedMode == PaymentMode.UPI ||
                    selectedMode == PaymentMode.PART_CASH_UPI ||
                    selectedMode == PaymentMode.PART_UPI_POS

    val isSplitMode =
            selectedMode == PaymentMode.PART_CASH_UPI ||
                    selectedMode == PaymentMode.PART_CASH_POS ||
                    selectedMode == PaymentMode.PART_UPI_POS

    LaunchedEffect(selectedMode, summary.total) {
        if (isSplitMode) {
            val split = BillCalculator.splitPartPayment(summary.total)
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
                else -> summary.total.toDoubleOrNull() ?: 0.0
            }
    val isAmountValid =
            if (isSplitMode) {
                BillCalculator.validatePartPayment(p1Text, p2Text, summary.total)
            } else true
    val canGenerateAmountQr =
        isUpiMode &&
            isAmountValid &&
            upiPayableAmount > 0.0 &&
            !profile?.upiHandle.isNullOrBlank()

    val relocationRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // Generate UPI QR off the main thread — ZXing encoding is CPU-heavy and caused UI freeze
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

    Column(
            modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.large)
                    .imePadding()
                    .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showQrCode) {
            Text("Scan to Pay", color = MaterialTheme.kbTertiary, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(spacing.medium))

            Box(
                modifier =
                Modifier.size(200.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(2.dp, MaterialTheme.kbPrimary, RoundedCornerShape(12.dp))
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
                color = MaterialTheme.kbSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = spacing.extraSmall)
            )
            if (!canGenerateAmountQr) {
                Text(
                    when {
                        profile?.upiHandle.isNullOrBlank() -> "Set UPI ID in Payment Configuration"
                        !isAmountValid -> "Enter a valid UPI split amount"
                        else -> "Add items before scanning UPI QR"
                    },
                    color = KbError,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = spacing.extraSmall)
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
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                },
                actions = {
                    TextButton(onClick = { showQrModal = false }) {
                        Text("Close", color = MaterialTheme.kbSecondary)
                    }
                }
            )
        }

        if (!isUpiMode) {
            Spacer(modifier = Modifier.height(spacing.large))
            Icon(
                Icons.Default.Payment,
                null,
                tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(KhanaBookTheme.iconSize.heroCircle)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(
                "Complete Payment",
                color = MaterialTheme.kbTextPrimary,
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {Text(
                        "Payable Amount", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                                "₹${"%.2f".format(summary.total.toDoubleOrNull() ?: 0.0)}",
                                color = MaterialTheme.kbSecondary,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            Text(
                    "Select Payment Mode:",
                    color = MaterialTheme.kbTertiary,
                    modifier = Modifier.align(Alignment.Start),
                    style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(56.dp)
                                    .background(MaterialTheme.kbPrimaryBold, RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.kbOutlineSubtle)
                                    .clickable { expanded = true }
                                    .padding(horizontal = spacing.medium),
                    contentAlignment = Alignment.CenterStart
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedMode.displayLabel, color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.kbSecondary)
                }
                DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.kbBgCard)
                ) {
                    enabledModes.forEach { mode ->
                        DropdownMenuItem(
                                text = { Text(mode.displayLabel, color = MaterialTheme.kbTextPrimary) },
                                onClick = {
                                    selectedMode = mode
                                    expanded = false
                                }
                        )
                    }
                }
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
                            "Sum must equal ${CurrencyUtils.formatPrice(summary.total)} (Current: ${CurrencyUtils.formatPrice(p1 + p2)})",
                            color = KbError,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = spacing.extraSmall).align(Alignment.Start)
                    )
                }
            }
        }





        Spacer(modifier = Modifier.height(spacing.extraLarge))

        if (selectedMode == PaymentMode.ONLINE) {
            val isOnline by viewModel.connectionStatus.collectAsState()
            if (isOnline != com.khanabook.lite.pos.domain.util.ConnectionStatus.Available) {
                Text(
                    "Internet connection required for online payment.",
                    color = KbError,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = spacing.medium)
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                        val serverBillId = viewModel.createDraftOnlineBill()
                        if (serverBillId != null) {
                            navController?.currentBackStackEntry
                                ?.savedStateHandle
                                ?.set("localBillId", viewModel.lastBill.value?.bill?.id)
                            val restaurantId = profile?.restaurantId ?: 0L
                            val amount = summary.total
                            navController?.navigate(
                                "easebuzz_payment/$restaurantId/$serverBillId/$amount"
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOnline == com.khanabook.lite.pos.domain.util.ConnectionStatus.Available) MaterialTheme.kbPrimary else Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = isOnline == com.khanabook.lite.pos.domain.util.ConnectionStatus.Available
            ) {
                Text(
                    "Pay Online (Easebuzz)",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            Button(
                    onClick = {
                        if (!isAmountValid) return@Button
                        scope.launch {
                            viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                            if (viewModel.completeOrder(PaymentStatus.SUCCESS)) {
                                viewModel.clearGatewayResult()
                                onComplete()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = if (isAmountValid) KbSuccess else Color.Gray
                            ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isAmountValid
            ) {
                Text(
                        "Payment Successful",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.smallMedium))
        TextButton(
                onClick = {
                    scope.launch {
                        viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                        if (selectedMode == PaymentMode.ONLINE) {
                            // ONLINE: cancel ALL pending drafts (including synced ones)
                            viewModel.cancelAllPendingOnlineDrafts()
                        } else {
                            viewModel.completeOrder(PaymentStatus.FAILED, "Customer left")
                        }
                        viewModel.clearGatewayResult()
                        PaymentReturnManager.clearLatestEvent()
                        onFailed()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                "Payment Failed / Cancelled",
                color = KbError,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun FailedStep(
        viewModel: BillingViewModel,
        onRetryPayment: () -> Unit,
        onNewBill: () -> Unit
) {
    val lastBill by viewModel.lastBill.collectAsState()
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
                .background(KbError.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, KbError.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Payment Failed",
                tint = KbError,
                modifier = Modifier.size(KhanaBookTheme.iconSize.avatar)
            )
        }

        Spacer(modifier = Modifier.height(spacing.large))

        Text(
            "Payment Failed / Cancelled",
            color = KbError,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            "Order #$orderDisplay · ₹${"%.2f".format(totalAmount)}",
            color = MaterialTheme.kbPrimary,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(spacing.small))

        Text(
            "The payment was not completed. The order has been recorded as failed.",
            color = MaterialTheme.kbTextPrimary.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = spacing.small)
        )

        Spacer(modifier = Modifier.height(spacing.extraLarge))

        // "Back to Home" button
        OutlinedButton(
            onClick = onNewBill,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Home, null, tint = MaterialTheme.kbSecondary)
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Back to Home", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.titleMedium)
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
    val lastBill by viewModel.lastBill.collectAsState()
    val printStatus by viewModel.printStatus.collectAsState()
    val receiptPrinting by viewModel.receiptPrinting.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val totalAmount = lastBill?.bill?.totalAmount?.toDoubleOrNull() ?: 0.0
    val scope = rememberCoroutineScope()
    var isSharingInvoice by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    val isSoundBoxEnabled = remember(lastBill?.bill?.id) {
        context.getSharedPreferences("session_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("is_sound_box_enabled", true)
    }

    DisposableEffect(context, isSoundBoxEnabled) {
        if (!isSoundBoxEnabled) return@DisposableEffect onDispose {}
        val engine = TextToSpeech(context.applicationContext) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale("en", "IN")
            }
        }
        tts.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts.value = null
        }
    }

    LaunchedEffect(isTtsReady, lastBill?.bill?.id, isSoundBoxEnabled) {
        if (!isSoundBoxEnabled || !isTtsReady || lastBill == null) return@LaunchedEffect
        tts.value?.speak(
            "Payment of ${formatAmountForSpeech(totalAmount)} received successfully.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "payment-success-${lastBill?.bill?.id}"
        )
    }

    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        PaymentSuccessBadge()
        Text(
                "Payment Successful!",
                color = MaterialTheme.kbTextPrimary,
                style = MaterialTheme.typography.headlineSmall
        )

        Text(
                "Payment of ₹${"%.2f".format(totalAmount)} received successfully.",
                color = MaterialTheme.kbTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(vertical = spacing.smallMedium)
        )

        Spacer(modifier = Modifier.height(spacing.extraLarge))
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
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                shape = RoundedCornerShape(12.dp),
                enabled = lastBill != null && !isSharingInvoice
            ) {
                if (isSharingInvoice) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize.small),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(iconSize.small))
                }
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text(
                    if (isSharingInvoice) "Preparing Link" else "Share Invoice",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Button(
                    onClick = {
                        lastBill?.let { viewModel.printReceipt(it) }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                    shape = RoundedCornerShape(12.dp),
                    enabled = lastBill?.let { it.bill.orderStatus != "cancelled" } == true && !receiptPrinting
            ) {
                if (receiptPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize.small),
                        color = MaterialTheme.kbTextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Receipt, null, tint = MaterialTheme.kbTextPrimary)
                }
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text(
                    if (receiptPrinting) "Preparing Invoice" else "Print Invoice",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.extraLarge))
        OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                shape = RoundedCornerShape(12.dp)
        ) {                        Text("Back to Home", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun PaymentSuccessBadge() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.Center
    ) {
        SuccessSpark(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 44.dp, top = 34.dp),
            color = MaterialTheme.kbSecondary
        )
        SuccessSpark(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 48.dp, top = 52.dp),
            color = KbSuccess
        )
        SuccessSpark(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 66.dp, bottom = 38.dp),
            color = KbSuccess.copy(alpha = 0.9f)
        )
        SuccessSpark(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 62.dp, bottom = 28.dp),
            color = MaterialTheme.kbPrimary.copy(alpha = 0.9f)
        )
        Surface(
            modifier = Modifier.size(132.dp),
            shape = CircleShape,
            color = KbSuccess.copy(alpha = 0.16f)
        ) {}
        Surface(
            modifier = Modifier.size(104.dp),
            shape = CircleShape,
            color = KbSuccess
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
private fun SuccessSpark(
    modifier: Modifier = Modifier,
    color: Color
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Surface(
            modifier = Modifier
                .width(26.dp)
                .height(4.dp),
            color = color.copy(alpha = 0.75f),
            shape = RoundedCornerShape(2.dp)
        ) {}
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
    val isVeg = type == "veg"
    val color = if (isVeg) KbSuccess else KbError
    // Content description tells screen readers whether the item is veg or non-veg
    // so the distinction is not conveyed by colour alone.
    // Shape differentiation: veg → circle, non-veg → rounded square (diamond-like indicator)
    val outerShape = if (isVeg) CircleShape else RoundedCornerShape(3.dp)
    val rotation = if (isVeg) 0f else 45f
    Box(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer(rotationZ = rotation)
            .border(1.5.dp, color, outerShape)
            .padding(3.dp)
            .semantics { contentDescription = if (isVeg) "Vegetarian" else "Non-vegetarian" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color, if (isVeg) CircleShape else RoundedCornerShape(2.dp))
                .graphicsLayer(rotationZ = -rotation)
        )
    }
}

@Composable
fun BillStepper(currentStep: Int) {
    val spacing = KhanaBookTheme.spacing
    val doneActive = currentStep >= 4
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
            endConnectorCompleted = doneActive,
            modifier = Modifier.weight(1f)
        )
        StepItem(
            icon = Icons.Default.CheckCircle,
            label = "Done",
            isActive = doneActive,
            isCompleted = currentStep == 4,
            showStartConnector = true,
            startConnectorCompleted = doneActive,
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
    val activeColor = Color.White
    val inactiveColor = MaterialTheme.kbTextSecondary
    val color = if (isActive) activeColor else inactiveColor
    val containerColor = if (isActive) KbBrandSaffron else MaterialTheme.kbBgSecondary
    val borderColor = if (isActive) KbBrandSaffron else MaterialTheme.kbOutlineSubtle

    // Spring-animated scale so step dots pop in when activated
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "step_scale"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showStartConnector) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(
                            if (startConnectorCompleted) KbBrandSaffron else MaterialTheme.kbBgSecondary
                        )
                )
            }
            if (showEndConnector) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(
                            if (endConnectorCompleted) KbBrandSaffron else MaterialTheme.kbBgSecondary
                        )
                )
            }
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(36.dp)
                    .background(containerColor, CircleShape)
                    .border(1.5.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isActive) Color.White else inactiveColor,
                    modifier = Modifier.size(18.dp)
                )
                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .background(KbBrandSaffron, CircleShape)
                            .border(1.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        )
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

