@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens.newbill

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.ConnectionStatus
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.feedback.performMenuItemAdd
import com.khanabook.lite.pos.ui.screens.CartItemNoteDialog
import com.khanabook.lite.pos.ui.screens.QuantitySelector
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import kotlinx.coroutines.launch


@Composable
fun OrderTypeButton(text: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
            modifier =
                    modifier.height(40.dp)
                            .background(
                                    if (isSelected) PrimaryGold else Color.Transparent,
                                    KhanaRadii.md
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
        onReturnToTableList: () -> Unit = {},
        onItemAddedFeedback: () -> Unit = {}
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
    val addItemWithFeedback = { addToCart: () -> Unit ->
        performMenuItemAdd(
            addToCart = addToCart,
            playFeedback = onItemAddedFeedback
        )
    }

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
                shape = KhanaRadii.lg
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
                            modifier = Modifier.size(KhanaBookTheme.iconSize.xlarge)
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
                            shape = KhanaRadii.lg
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
                                            onAdd = {
                                                addItemWithFeedback {
                                                    billingViewModel.addToCart(item)
                                                }
                                            },
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
                                                        onAdd = {
                                                            addItemWithFeedback {
                                                                billingViewModel.addToCart(item, variant)
                                                            }
                                                        },
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
                                    addItemWithFeedback {
                                        billingViewModel.addToCart(item, variant)
                                    }
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
                    shape = KhanaRadii.xl,
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
                                        shape = KhanaRadii.md,
                                        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.smallMedium),
                                        enabled = derivedItemCount > 0
                                    ) {
                                        Text(
                                            "Update Table",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleSmall
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
                                        shape = KhanaRadii.md,
                                        contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.smallMedium),
                                        enabled = derivedItemCount > 0
                                    ) {
                                        Text(
                                            "Save Table",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                            }

                            if (!canSaveTableOrder) {
                                Button(
                                        onClick = onProceedToPayment,
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkBrown1),
                                        shape = KhanaRadii.md,
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
                                    IconButton(onClick = { showNoteDialog = true }, modifier = Modifier.size(KhanaBookTheme.iconSize.large)) {
                                        Icon(
                                            if (cartItem.note.isNotBlank()) Icons.Default.EditNote else Icons.AutoMirrored.Filled.NoteAdd,
                                            contentDescription = "Add note",
                                            tint = if (cartItem.note.isNotBlank()) PrimaryGold else TextGold.copy(alpha = 0.5f),
                                            modifier = Modifier.size(KhanaBookTheme.iconSize.small)
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
                                        modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
                                        colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                                        shape = KhanaRadii.lg,
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
                                        modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
                                        colors = ButtonDefaults.buttonColors(containerColor = VegGreen),
                                        shape = KhanaRadii.lg,
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
                                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightLarge),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                                    shape = KhanaRadii.lg,
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
            modifier = Modifier.size(KhanaBookTheme.iconSize.xsmall).border(1.dp, color).padding(KhanaBookTheme.spacing.hairline),
            contentAlignment = Alignment.Center
    ) { Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(100.dp))) }
}

@Composable
internal fun menuTextFieldColors() =
        OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGold.copy(alpha = 0.3f),
                focusedBorderColor = PrimaryGold,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight
        )

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
