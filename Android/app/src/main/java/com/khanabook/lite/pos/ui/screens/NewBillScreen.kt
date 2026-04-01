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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.*
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.domain.util.CurrencyUtils
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

    LaunchedEffect(error) {
        error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            billingViewModel.clearError()
        }
    }

    
    LaunchedEffect(step) {
        if (step == 4) {
            android.widget.Toast.makeText(context, "Order Placed Successfully!", android.widget.Toast.LENGTH_SHORT).show()
        }
        if (step == 5) {
            android.widget.Toast.makeText(context, "Payment marked as failed.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = DarkBrown1,
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
                                onDone = onBack
                        )
                5 ->
                        FailedStep(
                                viewModel = billingViewModel,
                                onRetryPayment = { step = 3 },
                                onNewBill = onBack
                        )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.extraLarge),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = PrimaryGold)
                            Spacer(modifier = Modifier.height(spacing.medium))
                            Text(
                                text = "Saving Bill...",
                                color = TextLight,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
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
    val cartItems by billingViewModel.cartItems.collectAsStateWithLifecycle()
    val selectedCategoryId by menuViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val spacing = KhanaBookTheme.spacing
    
    // Adaptive split-view: Categories on left, Cart on right for tablets
    val isWideScreen = configuration.screenWidthDp >= 840
    val gridColumns = when {
        configuration.screenWidthDp >= 1200 -> 3
        configuration.screenWidthDp >= 600 -> 2
        else -> 1
    }

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

            if (categories.isNotEmpty()) {
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

            LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.weight(1f).padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.small + spacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small + spacing.extraSmall)
            ) {
                items(items, key = { it.menuItem.id }) { menuWithVariants ->
                    val item = menuWithVariants.menuItem
                    val variants = menuWithVariants.variants
                    var showVariantPicker by remember { mutableStateOf(false) }

                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                            shape = RoundedCornerShape(12.dp)
                    ) {
                        if (variants.isEmpty()) {

                            val cartItem =
                                    cartItems.find { it.item.id == item.id && it.variant == null }
                            Row(
                                    modifier = Modifier.padding(spacing.small + spacing.extraSmall),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                FoodTypeIcon(item.foodType)
                                Spacer(modifier = Modifier.width(spacing.small + spacing.extraSmall))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                            item.name,
                                            color = TextLight,
                                            style = MaterialTheme.typography.titleSmall
                                    )
                                    Text("₹${item.basePrice}", color = TextGold, style = MaterialTheme.typography.bodySmall)
                                }
                                QuantitySelector(
                                        quantity = cartItem?.quantity ?: 0,
                                        onAdd = { billingViewModel.addToCart(item) },
                                        onRemove = { billingViewModel.removeFromCart(item) }
                                )
                            }
                        } else {

                            Column(modifier = Modifier.padding(spacing.small + spacing.extraSmall)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FoodTypeIcon(item.foodType)
                                    Spacer(modifier = Modifier.width(spacing.small + spacing.extraSmall))
                                    Column {
                                        Text(
                                                item.name,
                                                color = TextLight,
                                                style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                                "${variants.size} Variants",
                                                color = PrimaryGold,
                                                style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(spacing.small))
                                variants.forEach { variant ->
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
                                                color = TextGold,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                                CurrencyUtils.formatPrice(variant.price),
                                                color = PrimaryGold,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(end = spacing.small + spacing.extraSmall)
                                        )
                                        QuantitySelector(
                                                quantity = variantCartItem?.quantity ?: 0,
                                                onAdd = { billingViewModel.addToCart(item, variant) },
                                                onRemove = {
                                                    billingViewModel.removeFromCart(item, variant)
                                                }
                                        )
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
                    Spacer(modifier = Modifier.height(if (isWideScreen) spacing.medium else 100.dp))
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
                                .padding(spacing.small + spacing.extraSmall),
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
                                contentPadding = PaddingValues(horizontal = spacing.large, vertical = spacing.small + spacing.extraSmall),
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
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(cartItems) { cartItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cartItem.item.name, color = TextLight, style = MaterialTheme.typography.bodyMedium)
                                    if (cartItem.variant != null) {
                                        Text(cartItem.variant.variantName, color = TextGold, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(
                                    "${cartItem.quantity} x ${CurrencyUtils.formatPrice(cartItem.variant?.price ?: cartItem.item.basePrice)}",
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            HorizontalDivider(color = BorderGold.copy(alpha = 0.1f))
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
                        }
                    }
                }
            }
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
                contentPadding = PaddingValues(horizontal = spacing.small + spacing.extraSmall)
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

    Column(
            modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.large)
                    .imePadding(),
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
                                    .padding(spacing.small + spacing.extraSmall)
                                    .clickable { showQrModal = true },
                    contentAlignment = Alignment.Center
            ) {
                if (!profile?.upiQrPath.isNullOrBlank()) {
                    AsyncImage(
                        model = profile?.upiQrPath,
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.QrCode,
                        null,
                        modifier = Modifier.size(100.dp),
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
                    modifier = Modifier.size(80.dp)
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

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small + spacing.extraSmall)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ParchmentTextField(
                                value = p1Text,
                                onValueChange = { p1Text = it },
                                label = labels.first,
                                modifier = Modifier.onFocusChanged { 
                                    if (it.isFocused) {
                                        scope.launch { relocationRequester.bringIntoView() }
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
                    Box(modifier = Modifier.weight(1f)) {
                        ParchmentTextField(
                                value = p2Text,
                                onValueChange = { p2Text = it },
                                label = labels.second,
                                modifier = Modifier.onFocusChanged { 
                                    if (it.isFocused) {
                                        scope.launch { relocationRequester.bringIntoView() }
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
        Spacer(modifier = Modifier.height(spacing.small + spacing.extraSmall))
        TextButton(
                onClick = {
                    scope.launch {
                        viewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                        viewModel.completeOrder(PaymentStatus.FAILED)
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
                            if (!profile?.upiQrPath.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile?.upiQrPath,
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
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
                .size(100.dp)
                .background(DangerRed.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, DangerRed.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Payment Failed",
                tint = DangerRed,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

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
        onDone: () -> Unit
) {
    val context = LocalContext.current
    val lastBill by viewModel.lastBill.collectAsState()
    val profile by settingsViewModel.profile.collectAsState()
    val spacing = KhanaBookTheme.spacing

    Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(100.dp))
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
                modifier = Modifier.padding(vertical = spacing.small + spacing.extraSmall)
        )

        Spacer(modifier = Modifier.height(spacing.extraLarge))
        Button(
                onClick = {
                    lastBill?.let { shareBillOnWhatsApp(context, it, profile) }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                shape = RoundedCornerShape(12.dp),
                enabled = lastBill != null
        ) {
            Icon(Icons.Default.Share, null, tint = Color.White)
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Share Invoice on WhatsApp", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(spacing.medium))
        Button(
                onClick = {
                    lastBill?.let { directPrint(context, it, profile, viewModel.printerManager) }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                shape = RoundedCornerShape(12.dp),
                enabled = lastBill != null
        ) {
            Icon(Icons.Default.Print, null, tint = DarkBrown1)
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Print Bill", color = DarkBrown1, style = MaterialTheme.typography.titleMedium)
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
    val spacing = KhanaBookTheme.spacing
    AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DarkBrown2,
            title = {
                Column {
                    Text("Choose Variant", color = PrimaryGold, style = MaterialTheme.typography.titleLarge)
                    Text(itemName, color = TextGold, style = MaterialTheme.typography.bodySmall)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    variants.forEach { variant ->
                        Card(
                                modifier =
                                        Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall).clickable {
                                            onSelect(variant)
                                        },
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = Color.Black.copy(alpha = 0.3f)
                                        ),
                                shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(horizontal = spacing.medium, vertical = spacing.small + spacing.extraSmall),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                        variant.variantName,
                                        color = TextLight,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                )
                                Text(
                                        "₹${"%.0f".format(variant.price)}",
                                        color = PrimaryGold,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = PrimaryGold, style = MaterialTheme.typography.labelLarge) }
            }
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
