@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import android.graphics.BitmapFactory
import coil.compose.AsyncImage
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
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
import kotlinx.coroutines.launch

@Composable
fun NewBillScreen(
        onBack: () -> Unit,
        modifier: Modifier = Modifier,
        billingViewModel: BillingViewModel = hiltViewModel(),
        menuViewModel: MenuViewModel = hiltViewModel(),
        settingsViewModel: SettingsViewModel = hiltViewModel(),
        navController: androidx.navigation.NavController? = null
) {
    var step by remember { mutableIntStateOf(1) }
    val cartItems by billingViewModel.cartItems.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing

    // Intercept system back gesture to navigate through steps
    androidx.activity.compose.BackHandler(enabled = step > 1) {
        when (step) {
            2 -> step = 1
            3 -> step = 2
            else -> onBack() // step 4 (success) and step 5 (failed): exit screen
        }
    }

    val summary by billingViewModel.billSummary.collectAsStateWithLifecycle()
    val error by billingViewModel.error.collectAsStateWithLifecycle()
    val isLoading by billingViewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
        containerColor = DarkBrown1,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(DarkBrown1)) {
                CenterAlignedTopAppBar(
                    title = { Text("New Bill", color = PrimaryGold, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = { if (step == 1) onBack() else if (step == 2) step = 1 else if (step == 3) step = 2 else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryGold)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkBrown1)
                )
                
                if (step < 4) {
                    BillStepper(currentStep = step)
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .background(DarkBrown1)
        ) {
            when (step) {
                1 ->
                        CustomerInfoStep(
                                onNext = { name, whatsapp ->
                                    billingViewModel.setCustomerInfo(name, whatsapp)
                                    step = 2
                                },
                                onBack = onBack,
                                hideHeader = true
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
                                onFailed = { step = 5 }
                        )
                4 ->
                        SuccessStep(
                                billingViewModel,
                                settingsViewModel,
                                onDone = onBack,
                                onShowMessage = { msg -> coroutineScope.launch { snackbarHostState.showSnackbar(msg) } }
                        )
                5 ->
                        FailedStep(
                                viewModel = billingViewModel,
                                onRetryPayment = { step = 3 },
                                onNewBill = onBack
                        )
            }

            KhanaBookLoadingOverlay(
                visible = isLoading,
                type = LoadingType.SAVING
            )
        }
    }
}

@Composable
fun CustomerInfoStep(onNext: (String, String) -> Unit, onBack: () -> Unit, hideHeader: Boolean = false) {
    var name by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    val spacing = KhanaBookTheme.spacing

    
    val isWhatsappValid = whatsapp.isNotEmpty() && ValidationUtils.isValidPhone(whatsapp)
    val isNextEnabled = isWhatsappValid

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
                    Text("Customer Details", color = TextGold, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }

        val showPhoneError = whatsapp.isNotEmpty() && !ValidationUtils.isValidPhone(whatsapp)
        OutlinedTextField(
                value = whatsapp,
                onValueChange = { whatsapp = it.filter { ch -> ch.isDigit() }.take(10) },
                label = { Text("Customer WhatsApp Number *") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = VegGreen) },
                isError = showPhoneError,
                supportingText = {
                    if (showPhoneError) Text("Enter 10-digit number", color = DangerRed)
                },
                keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        )
        )
        Spacer(modifier = Modifier.height(spacing.medium))
        OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Customer Name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
                leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryGold) }
        )

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
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val displayItems = if (searchQuery.isNotBlank()) searchResults else items
    
    // Adaptive split-view: Categories on left, Cart on right for tablets
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

            if (categories.isEmpty() && displayItems.isEmpty()) {
                // Skeleton while menu data loads
                SkeletonMenuScreen(modifier = Modifier.weight(1f))
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
                            Column(modifier = Modifier.padding(spacing.smallMedium)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FoodTypeIcon(item.foodType)
                                    Spacer(modifier = Modifier.width(spacing.smallMedium))
                                    Column {
                                        Text(
                                                item.name,
                                                color = if (itemAvailable) TextLight else TextLight.copy(alpha = 0.4f),
                                                style = MaterialTheme.typography.titleSmall
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
                        Button(
                                onClick = onProceedToPayment,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkBrown1),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = spacing.large, vertical = spacing.smallMedium),
                                enabled = derivedItemCount > 0
                        ) {
                            Text(
                                    "Proceed",
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.titleMedium
                            )
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
                                        Text(cartItem.item.name, color = TextLight, style = MaterialTheme.typography.bodyMedium)
                                        if (cartItem.variant != null) {
                                            Text(cartItem.variant.variantName, color = TextGold, style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (cartItem.note.isNotBlank()) {
                                            Text("Note: ${cartItem.note}", color = PrimaryGold.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text(
                                        "${cartItem.quantity} x ${CurrencyUtils.formatPrice(cartItem.variant?.price ?: cartItem.item.basePrice)}",
                                        color = TextLight,
                                        style = MaterialTheme.typography.bodySmall
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
                            Button(
                                onClick = onProceedToPayment,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                shape = RoundedCornerShape(12.dp),
                                enabled = derivedItemCount > 0
                            ) {
                                Text("Proceed to Payment", color = DarkBrown1, style = MaterialTheme.typography.titleMedium)
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
private fun CartItemNoteDialog(initialNote: String, itemName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
        TextButton(onClick = { onSave(noteText.trim()) }) {
            Text("Save", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextGold, style = MaterialTheme.typography.labelLarge)
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
fun PaymentStep(viewModel: BillingViewModel, settingsViewModel: SettingsViewModel, onBackToMenu: () -> Unit, onComplete: () -> Unit, onFailed: () -> Unit = {}) {
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
            selectedMode =
                    if (enabledModes.contains(PaymentMode.UPI)) PaymentMode.UPI
                    else enabledModes.first()
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
            val totalVal = summary.total.toDoubleOrNull() ?: 0.0
            val half = totalVal / 2.0
            p1Text = "%.2f".format(half)
            p2Text = "%.2f".format(totalVal - half)
        }
    }

    val p1 = p1Text.toDoubleOrNull() ?: 0.0
    val p2 = p2Text.toDoubleOrNull() ?: 0.0
    val isAmountValid =
            if (isSplitMode) {
                BillCalculator.validatePartPayment(p1Text, p2Text, summary.total)
            } else true

    val relocationRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // Generate a live UPI QR with the exact bill amount when upiHandle is configured
    // but no static QR image has been saved. Regenerates whenever the total changes.
    val dynamicUpiQrBitmap = remember(profile?.upiHandle, summary.total) {
        val handle = profile?.upiHandle
        if (!handle.isNullOrBlank()) {
            val amount = summary.total.toDoubleOrNull() ?: 0.0
            QrCodeManager.generateUpiQr(handle, profile?.shopName ?: "RESTAURANT", amount, 512)
        } else null
    }

    Column(
            modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.large)
                    .imePadding()
                    .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isUpiMode) {
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
                when {
                    dynamicUpiQrBitmap != null -> Image(
                        bitmap = dynamicUpiQrBitmap.asImageBitmap(),
                        contentDescription = "Scan to pay ${profile?.upiHandle}",
                        modifier = Modifier.fillMaxSize()
                    )
                    !profile?.upiQrPath.isNullOrBlank() -> AsyncImage(
                        model = AppAssetStore.resolveAssetPath(profile?.upiQrPath),
                        contentDescription = "QR Code",
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
                    color = PrimaryGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = spacing.extraSmall)
            )

            Spacer(modifier = Modifier.height(spacing.large))
        } else {
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
                colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(spacing.medium)) {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Payable Amount", color = TextGold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                                "₹${"%.2f".format(summary.total.toDoubleOrNull() ?: 0.0)}",
                                color = PrimaryGold,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            Text(
                    "Select Payment Mode:",
                    color = TextLight,
                    modifier = Modifier.align(Alignment.Start),
                    style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(56.dp)
                                    .background(DarkBrown2, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderGold)
                                    .clickable { expanded = true }
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
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(DarkBrown2)
                ) {
                    enabledModes.forEach { mode ->
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
                            color = DangerRed,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = spacing.extraSmall).align(Alignment.Start)
                    )
                }
            }
        }

        val scope = rememberCoroutineScope()

        Spacer(modifier = Modifier.height(spacing.extraLarge))
        Button(
                onClick = {
                    if (isAmountValid) {
                        scope.launch {
                            viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                            if (viewModel.completeOrder(PaymentStatus.SUCCESS)) {
                                onComplete()
                            }
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
                enabled = isAmountValid
        ) {
            Text(
                    "Payment Successful",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(spacing.smallMedium))
        TextButton(
                onClick = {
                    scope.launch {
                        viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                        viewModel.completeOrder(PaymentStatus.FAILED, "Customer left")
                        onFailed()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Payment Failed / Cancelled", color = DangerRed, style = MaterialTheme.typography.bodyMedium) }
    }

    if (showQrModal) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showQrModal = false }) {
            Card(
                    modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                "UPI Payment",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.Black
                        )
                        IconButton(onClick = { showQrModal = false }) {
                            Icon(Icons.Default.Close, null, tint = Color.Black)
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(
                            color = Color.LightGray.copy(alpha = 0.5f)
                    )

                    Column(
                            modifier = Modifier.fillMaxWidth().padding(spacing.large),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                                modifier =
                                        Modifier.size(280.dp).background(Color.White).padding(spacing.small),
                                contentAlignment = Alignment.Center
                        ) {
                            when {
                                dynamicUpiQrBitmap != null -> Image(
                                    bitmap = dynamicUpiQrBitmap.asImageBitmap(),
                                    contentDescription = "Scan to pay ${profile?.upiHandle}",
                                    modifier = Modifier.fillMaxSize()
                                )
                                !profile?.upiQrPath.isNullOrBlank() -> AsyncImage(
                                    model = AppAssetStore.resolveAssetPath(profile?.upiQrPath),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> Icon(
                                    Icons.Default.QrCode,
                                    null,
                                    modifier = Modifier.size(180.dp),
                                    tint = Color.LightGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.medium))
                        Text(
                                "₹${"%.2f".format(summary.total.toDoubleOrNull() ?: 0.0)}",
                                color = Color.Black,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(profile?.shopName ?: "", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                            onClick = { showQrModal = false },
                            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkBrown1)
                    ) { Text("CLOSE", color = PrimaryGold, style = MaterialTheme.typography.titleMedium) }
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
    val lastBill by viewModel.lastBill.collectAsState()
    val printStatus by viewModel.printStatus.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(KhanaBookTheme.iconSize.hero))
        Text(
                "Payment Successful!",
                color = TextLight,
                style = MaterialTheme.typography.headlineSmall
        )

        val totalAmount = lastBill?.bill?.totalAmount?.toDoubleOrNull() ?: 0.0
        Text(
                "Payment of ₹${"%.2f".format(totalAmount)} received successfully.",
                color = TextGold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = spacing.smallMedium)
        )

        Spacer(modifier = Modifier.height(spacing.extraLarge))
        LaunchedEffect(printStatus) {
            printStatus?.let { onShowMessage(it) }
        }
        printStatus?.let { status ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardBG.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Print, null, tint = PrimaryGold, modifier = Modifier.size(iconSize.medium))
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(status, color = TextLight, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(spacing.medium))
        }
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Button(
                    onClick = {
                        lastBill?.let { shareBillOnWhatsApp(context, it, profile) }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp),
                    enabled = lastBill != null
            ) {
                Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(iconSize.small))
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text("PDF", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Button(
                    onClick = {
                        lastBill?.let { shareBillTextOnWhatsApp(context, it, profile) }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                    shape = RoundedCornerShape(12.dp),
                    enabled = lastBill != null
            ) {
                Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(iconSize.small))
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text("Text", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(modifier = Modifier.height(spacing.medium))
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Button(
                    onClick = {
                        lastBill?.let { viewModel.printReceipt(it) }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = RoundedCornerShape(12.dp),
                    enabled = lastBill?.let { it.bill.orderStatus != "cancelled" } ?: false
            ) {
                Icon(Icons.Default.Receipt, null, tint = DarkBrown1)
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text("Receipt", color = DarkBrown1, style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                    onClick = {
                        lastBill?.let { viewModel.printKitchenTicket(it) }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    border = BorderStroke(1.dp, PrimaryGold),
                    enabled = lastBill?.let { it.bill.orderStatus != "cancelled" } ?: false
            ) {
                Icon(Icons.Default.Restaurant, null, tint = PrimaryGold)
                Spacer(modifier = Modifier.width(spacing.extraSmall))
                Text("Reprint KDS", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(modifier = Modifier.height(spacing.extraLarge))
        OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGold),
                shape = RoundedCornerShape(12.dp)
        ) { Text("Back to Home", color = TextGold, style = MaterialTheme.typography.titleMedium) }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.medium, start = spacing.extraLarge, end = spacing.extraLarge),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Center
    ) {
        StepItem(icon = Icons.Default.Person, label = "Customer", isActive = currentStep >= 1, isCompleted = currentStep > 1)
        StepConnector(isCompleted = currentStep > 1)
        StepItem(icon = Icons.AutoMirrored.Filled.List, label = "Menu", isActive = currentStep >= 2, isCompleted = currentStep > 2)
        StepConnector(isCompleted = currentStep > 2)
        StepItem(icon = Icons.Default.Payments, label = "Payment", isActive = currentStep >= 3, isCompleted = currentStep > 3)
    }
}

@Composable
fun StepItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, isCompleted: Boolean) {
    val color = if (isActive) PrimaryGold else Color.Gray
    val containerColor = if (isActive) PrimaryGold.copy(alpha = 0.1f) else Color.Transparent
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(containerColor, CircleShape)
                .border(1.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = color,
                modifier = Modifier.size(18.dp)
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
        Text(label, color = color, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
    }
}

@Composable
fun RowScope.StepConnector(isCompleted: Boolean) {
    val color = if (isCompleted) PrimaryGold else Color.Gray
    val spacing = KhanaBookTheme.spacing
    // padding(top=17.dp) aligns the 1dp line with the center of the 36dp icon circle
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(top = 17.dp, start = spacing.extraSmall, end = spacing.extraSmall)
            .height(1.dp)
            .background(color)
    )
}
