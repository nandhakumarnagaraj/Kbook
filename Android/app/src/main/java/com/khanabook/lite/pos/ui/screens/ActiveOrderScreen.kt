@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
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
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel.CartItem
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel.BillSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import java.math.BigDecimal
import kotlin.math.roundToLong

@Composable
fun ActiveOrderScreen(
    draftBillId: Long,
    onBack: () -> Unit,
    onOrderCompleted: () -> Unit,
    billingViewModel: BillingViewModel = hiltViewModel(),
    menuViewModel: MenuViewModel = hiltViewModel()
) {
    var isLoaded by remember { mutableStateOf(false) }
    var showVariantPickerFor by remember { mutableStateOf<MenuItemEntity?>(null) }
    var showItemNoteFor by remember { mutableStateOf<CartItem?>(null) }

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
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(2.dp, PrimaryGold, RoundedCornerShape(12.dp))
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
                            billingViewModel.appendItemsToDraft(draftBillId)
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
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAmountValid) SuccessGreen else Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isAmountValid && itemCount > 0 && !isLoading
                ) {
                    Text("Payment Successful — ${CurrencyUtils.formatPrice(total)}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            billingViewModel.setPaymentMode(selectedMode, p1Text, p2Text)
                            billingViewModel.settleDraftOrder(draftBillId, selectedMode, PaymentStatus.FAILED)
                            billingViewModel.clearActiveSession()
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                    border = BorderStroke(1.dp, DangerRed),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Text("Payment Failed / Cancel", style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            billingViewModel.appendItemsToDraft(draftBillId)
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                    shape = RoundedCornerShape(12.dp),
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

@Composable
private fun BillInfoHeader(
    customerName: String,
    customerWhatsapp: String,
    orderType: String,
    itemCount: Int,
    total: Double
) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (customerName.isNotBlank()) customerName else "Unnamed",
                    color = TextLight,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (customerWhatsapp.isNotBlank()) {
                    Text(
                        text = customerWhatsapp,
                        color = TextGold.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                KhanaStatusBadge(
                    text =
                        when (orderType.trim().lowercase()) {
                            "dine_in", "dine-in" -> "DINE-IN"
                            "online", "online_order", "parcel" -> "ONLINE ORDER"
                            else -> "TAKEAWAY"
                        },
                    kind = KhanaStatusKind.Info
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    text = "$itemCount items • ${CurrencyUtils.formatPrice(total)}",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ActiveOrderItemCard(
    cartItem: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onShowVariantPicker: () -> Unit,
    onShowNote: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val item = cartItem.item
    val variant = cartItem.variant
    val price = variant?.price ?: item.basePrice
    val itemTotal = BigDecimal(price).multiply(BigDecimal.valueOf(cartItem.quantity.toLong()))
        .setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = TextLight,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (variant != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = variant.variantName,
                        color = PrimaryGold,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "Change",
                        color = PrimaryGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.clickable { onShowVariantPicker() }
                    )
                }
            } else {
                Text(
                    text = CurrencyUtils.formatPrice(price),
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (cartItem.note.isNotBlank()) {
                Text(
                    text = "Note: ${cartItem.note}",
                    color = PrimaryGold.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "${cartItem.quantity} × ${CurrencyUtils.formatPrice(price)}",
            color = TextGold,
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(modifier = Modifier.width(spacing.small))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background(PrimaryGold, RoundedCornerShape(4.dp)).height(28.dp)
        ) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, null, tint = DarkBrown1, modifier = Modifier.size(14.dp))
            }
            Text("${cartItem.quantity}", color = DarkBrown1, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            IconButton(onClick = onIncrement, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, null, tint = DarkBrown1, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(modifier = Modifier.width(spacing.extraSmall))

        IconButton(onClick = onShowNote, modifier = Modifier.size(28.dp)) {
            Icon(
                if (cartItem.note.isNotBlank()) Icons.Default.EditNote else Icons.AutoMirrored.Filled.NoteAdd,
                contentDescription = "Note",
                tint = if (cartItem.note.isNotBlank()) PrimaryGold else TextGold.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CompactMenuSection(
    menuViewModel: MenuViewModel,
    billingViewModel: BillingViewModel
) {
    val categories by menuViewModel.categories.collectAsStateWithLifecycle()
    val items by menuViewModel.menuItems.collectAsStateWithLifecycle()
    val searchResults by menuViewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by menuViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryId by menuViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val totalItemsCount by menuViewModel.totalItemsCount.collectAsStateWithLifecycle()
    val isCatalogLoaded by menuViewModel.isCatalogLoaded.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val gridColumns = layout.menuGridColumns
    val displayItems = if (searchQuery.isNotBlank()) searchResults else items

    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            menuViewModel.selectCategory(categories.first().id)
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
        modifier = Modifier.fillMaxWidth(),
        colors = menuTextFieldColors(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )

    if (searchQuery.isBlank() && categories.isNotEmpty()) {
        val selectedIndex = categories.indexOfFirst { it.id == selectedCategoryId }.coerceAtLeast(0)
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = PrimaryGold,
            contentColor = DarkBrown1,
            edgePadding = spacing.small,
            divider = {},
            indicator = { tabPositions ->
                if (selectedIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
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
                            style = MaterialTheme.typography.labelSmall.copy(
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

    if (!isCatalogLoaded && categories.isEmpty()) {
        Box(modifier = Modifier.height(120.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryGold)
        }
    } else if (displayItems.isEmpty()) {
        Text(
            if (searchQuery.isNotBlank()) "No items match \"$searchQuery\"" else "No items",
            color = TextGold.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            items(displayItems.take(8), key = { it.menuItem.id }) { menuWithVariants ->
                val item = menuWithVariants.menuItem
                val variants = menuWithVariants.variants
                val itemAvailable = item.isAvailable

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (itemAvailable) DarkBrown2 else DarkBrown2.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (variants.isEmpty()) {
                        Row(
                            modifier = Modifier.padding(spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    color = if (itemAvailable) TextLight else TextLight.copy(alpha = 0.4f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    CurrencyUtils.formatPrice(item.basePrice),
                                    color = TextGold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (itemAvailable) {
                                OutlinedButton(
                                    onClick = { billingViewModel.addToCart(item) },
                                    modifier = Modifier.height(28.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                                    border = BorderStroke(1.dp, PrimaryGold),
                                    contentPadding = PaddingValues(horizontal = spacing.small)
                                ) {
                                    Text("Add", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.padding(spacing.small)) {
                            Text(
                                item.name,
                                color = if (itemAvailable) TextLight else TextLight.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (itemAvailable) {
                                variants.forEach { variant ->
                                    val variantAvailable = variant.isAvailable
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${variant.variantName} • ${CurrencyUtils.formatPrice(variant.price)}",
                                            color = if (variantAvailable) TextGold else TextGold.copy(alpha = 0.35f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        if (variantAvailable) {
                                            IconButton(
                                                onClick = { billingViewModel.addToCart(item, variant) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Add, null, tint = PrimaryGold, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (displayItems.size > 8) {
            Text(
                "+${displayItems.size - 8} more items. Use search to find specific items.",
                color = TextGold.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BillSummaryDisplay(summary: BillSummary) {
    val spacing = KhanaBookTheme.spacing
    val subtotal = summary.subtotal.toDoubleOrNull() ?: 0.0
    val cgst = summary.cgst.toDoubleOrNull() ?: 0.0
    val sgst = summary.sgst.toDoubleOrNull() ?: 0.0
    val customTax = summary.customTax.toDoubleOrNull() ?: 0.0
    val total = summary.total.toDoubleOrNull() ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            SummaryRow("Subtotal", CurrencyUtils.formatPrice(subtotal), TextGold)
            if (cgst > 0) SummaryRow("CGST", CurrencyUtils.formatPrice(cgst), TextGold.copy(alpha = 0.7f))
            if (sgst > 0) SummaryRow("SGST", CurrencyUtils.formatPrice(sgst), TextGold.copy(alpha = 0.7f))
            if (customTax > 0) SummaryRow("Custom Tax", CurrencyUtils.formatPrice(customTax), TextGold.copy(alpha = 0.7f))
            HorizontalDivider(color = BorderGold.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = spacing.small))
            SummaryRow("Total", CurrencyUtils.formatPrice(total), PrimaryGold, bold = true)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = color, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            color = color,
            style = if (bold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PaymentModeSelector(
    enabledModes: List<PaymentMode>,
    selectedMode: PaymentMode,
    onModeChange: (PaymentMode) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(BrownSelected, RoundedCornerShape(8.dp))
            .border(1.dp, BorderGold)
            .clickable { onExpandedChange(true) }
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
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(DarkBrown2)
        ) {
            enabledModes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayLabel, color = TextLight) },
                    onClick = {
                        onModeChange(mode)
                        onExpandedChange(false)
                    }
                )
            }
        }
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
        try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
    }
}

@Composable private fun menuTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextLight,
    unfocusedTextColor = TextLight,
    cursorColor = PrimaryGold,
    focusedBorderColor = PrimaryGold,
    unfocusedBorderColor = BorderGold.copy(alpha = 0.3f),
    focusedLabelColor = PrimaryGold,
    unfocusedLabelColor = TextGold
)
