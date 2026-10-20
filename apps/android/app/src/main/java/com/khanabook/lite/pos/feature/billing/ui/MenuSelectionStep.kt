@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.billing.ui
import com.khanabook.lite.pos.domain.model.OrderType
import com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode


import android.util.Log
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
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
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import com.khanabook.lite.pos.feature.sync.domain.ConnectionStatus
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.menu.ui.performMenuItemAdd
import com.khanabook.lite.pos.feature.billing.ui.CartItemNoteDialog
import com.khanabook.lite.pos.feature.billing.ui.QuantitySelector
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.feature.billing.viewmodel.BillingViewModel
import com.khanabook.lite.pos.feature.menu.viewmodel.MenuViewModel
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
    val categories by menuViewModel.activeCategories.collectAsStateWithLifecycle()
    val items by menuViewModel.menuItems.collectAsStateWithLifecycle()
    val searchResults by menuViewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by menuViewModel.searchQuery.collectAsStateWithLifecycle()
    val catalog by menuViewModel.catalogState.collectAsStateWithLifecycle()
    val cartItems by billingViewModel.cartItems.collectAsStateWithLifecycle()
    val selectedCategoryId by menuViewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val isMenuItemsLoading by menuViewModel.isMenuItemsLoading.collectAsStateWithLifecycle()
    val favouritesOnly by menuViewModel.favouritesOnly.collectAsStateWithLifecycle()
    val favouriteItems by menuViewModel.favouriteMenuItems.collectAsStateWithLifecycle()
    val connectionStatus by billingViewModel.connectionStatus.collectAsStateWithLifecycle()
    val isOffline = connectionStatus == ConnectionStatus.Unavailable
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    // Favourites is a view of its own rather than a category filter: the list spans every
    // category (the store's pins first, then this till's own stars), which is exactly what a
    // cashier needs at the counter - one tap to the dishes this shop actually sells.
    val displayItems = when {
        searchQuery.isNotBlank() -> searchResults
        favouritesOnly -> favouriteItems
        else -> items
    }
    // "Loaded" and the item count now come from one atomic emission, so this can no
    // longer be true while the count is still its initial zero.
    val hasNoMenuItems = catalog.loaded && catalog.totalItemCount == 0 && searchQuery.isBlank()
    // [DEBUG-kbcat]
    LaunchedEffect(catalog.loaded, catalog.totalItemCount, categories.size, displayItems.size, selectedCategoryId, hasNoMenuItems, isMenuItemsLoading) {
        Log.i(
            "KB-CATALOG",
            "[DEBUG-kbcat] loaded=${catalog.loaded} total=${catalog.totalItemCount} cats=${categories.size} " +
                "display=${displayItems.size} selCat=$selectedCategoryId loading=$isMenuItemsLoading noMenu=$hasNoMenuItems"
        )
    }
    
    // Adaptive split-view: Categories on left, Cart on right for tablets
    val isWideScreen = layout.isWideListDetail
    val scope = rememberCoroutineScope()
    val quickMode by billingViewModel.quickMode.collectAsStateWithLifecycle()
    val currentOrderType by billingViewModel.orderType.collectAsStateWithLifecycle()
    val profile by billingViewModel.cachedProfile.collectAsStateWithLifecycle()
    val paymentFlowMode = OrderPaymentFlowMode.fromDbValue(profile?.orderPaymentFlowMode)
    val canSaveTableOrder = currentOrderType == OrderType.DINE_IN &&
        (paymentFlowMode == OrderPaymentFlowMode.PAY_AFTER_FOOD || billingViewModel.editingBillId != null)
    val addItemWithFeedback = { addToCart: () -> Unit ->
        performMenuItemAdd(
            addToCart = addToCart,
            playFeedback = onItemAddedFeedback
        )
    }

    val derivedItemCount by remember {
        derivedStateOf { cartItems.sumOf { it.quantity } }
    }

    var isSavingTable by remember { mutableStateOf(false) }

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

            if (quickMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrderTypeButton(
                        text = "Dine-In",
                        isSelected = currentOrderType == OrderType.DINE_IN,
                        modifier = Modifier.weight(1f),
                        onClick = { billingViewModel.setOrderType(OrderType.DINE_IN) }
                    )
                    OrderTypeButton(
                        text = "Takeaway",
                        isSelected = currentOrderType == OrderType.TAKEAWAY,
                        modifier = Modifier.weight(1f),
                        onClick = { billingViewModel.setOrderType(OrderType.TAKEAWAY) }
                    )
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

            // Category and Favourites scroll bar: Favourites is the first tab in the scrollable strip,
            // followed by all available categories.
            if (searchQuery.isBlank() && (categories.isNotEmpty() || favouriteItems.isNotEmpty())) {
                val selectedIndex = if (favouritesOnly) {
                    0
                } else {
                    val catIdx = categories.indexOfFirst { it.id == selectedCategoryId }
                    if (catIdx >= 0) catIdx + 1 else 0
                }
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
                    Tab(
                        selected = favouritesOnly,
                        onClick = {
                            menuViewModel.setFavouritesOnly(true)
                        },
                        text = {
                            Text(
                                "Favourites",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (favouritesOnly) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        selectedContentColor = DarkBrown1,
                        unselectedContentColor = DarkBrown1.copy(alpha = 0.7f)
                    )
                    categories.forEach { category ->
                        val isSelected = !favouritesOnly && category.id == selectedCategoryId
                        Tab(
                                selected = isSelected,
                                onClick = {
                                    menuViewModel.setFavouritesOnly(false)
                                    menuViewModel.selectCategory(category.id)
                                },
                                text = {
                                    Text(
                                            category.name,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                    )
                                },
                                selectedContentColor = DarkBrown1,
                                unselectedContentColor = DarkBrown1.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // The per-category loading flag must not cover the favourites view: it is only ever true
    // for the category query, so leaving it in would show a skeleton over a list that has
    // already loaded (and could be legitimately empty).
    val isInitialLoading =
        (!catalog.loaded || (isMenuItemsLoading && displayItems.isEmpty() && !favouritesOnly)) && searchQuery.isBlank()

            if (isInitialLoading) {
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
                            when {
                                // Search wins: it replaces the list regardless of the view, so
                                // its "no matches" message has to win the message too.
                                searchQuery.isNotBlank() -> "No items match \"$searchQuery\""
                                favouritesOnly -> "Nothing starred yet. Star a dish in Menu Configuration to see it here."
                                else -> "No items in this category"
                            },
                            color = TextGold.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // Compute columns from actual available pane width (not global screen width).
                // This handles split-pane correctly: when the cart panel takes ~40% of 840dp,
                // the grid pane is only ~500dp wide and should show 2 columns, not 3.
                val paneColumns = when {
                    maxWidth >= 720.dp -> 4
                    maxWidth >= 480.dp -> 3
                    maxWidth >= 280.dp -> 2
                    else -> 1
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(paneColumns),
                    // Bottom inset is owned by the trailing spacer below so
                    // phones and wide screens share one consistent gap above
                    // the bottom cart card (same rhythm as ManualMenuView).
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = spacing.medium, end = spacing.medium, top = spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.smallMedium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.smallMedium)
            ) {
                items(displayItems, key = { it.menuItem.id }) { menuWithVariants ->
                    val item = menuWithVariants.menuItem
                    val variants = menuWithVariants.variants
                    var showVariantPicker by remember { mutableStateOf(false) }
                    val cartItem = cartItems.find { it.item.id == item.id && it.variant == null }
                    val cartQuantity = if (variants.isEmpty()) {
                        cartItem?.quantity ?: 0
                    } else {
                        cartItems.filter { it.item.id == item.id }.sumOf { it.quantity }
                    }

                    SquareMenuItemCard(
                        item = item,
                        variants = variants,
                        cartQuantity = cartQuantity,
                        onCardClick = {
                            if (variants.isNotEmpty()) {
                                showVariantPicker = true
                            } else {
                                addItemWithFeedback {
                                    billingViewModel.addToCart(item)
                                }
                            }
                        },
                        onAdd = {
                            addItemWithFeedback {
                                billingViewModel.addToCart(item)
                            }
                        },
                        onRemove = {
                            billingViewModel.removeFromCart(item)
                        }
                    )

                    if (showVariantPicker) {
                        VariantPickerDialog(
                            itemName = item.name,
                            variants = variants,
                            cartItems = cartItems.filter { it.item.id == item.id },
                            onDismiss = { showVariantPicker = false },
                            onAddVariant = { variant ->
                                addItemWithFeedback {
                                    billingViewModel.addToCart(item, variant)
                                }
                            },
                            onRemoveVariant = { variant ->
                                billingViewModel.removeFromCart(item, variant)
                            }
                        )
                    }
                }
                
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(paneColumns) }) {
                    Spacer(modifier = Modifier.height(spacing.extraLarge))
                }
            }
            } // close BoxWithConstraints


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
                                            if (isSavingTable) return@Button
                                            isSavingTable = true
                                            scope.launch {
                                                val billId = billingViewModel.editingBillId ?: run { isSavingTable = false; return@launch }
                                                val ok = billingViewModel.appendItemsToDraft(billId)
                                                isSavingTable = false
                                                if (ok) {
                                                    onShowMessage("Table updated")
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
                                        enabled = derivedItemCount > 0 && !isSavingTable
                                    ) {
                                        Text(
                                            "Update & Send KOT",
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (isSavingTable) return@Button
                                            isSavingTable = true
                                            scope.launch {
                                                val ok = billingViewModel.saveDraftOrder(billingViewModel.customerName.value)
                                                isSavingTable = false
                                                if (ok) {
                                                    onShowMessage("Table saved")
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
                                        enabled = derivedItemCount > 0 && !isSavingTable
                                    ) {
                                        Text(
                                            "Send to Kitchen (KOT)",
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
                                            if (billingViewModel.editingBillId != null) "Settle" else "Proceed to Pay",
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
                        contentPadding = PaddingValues(bottom = spacing.extraLarge)
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .background(PrimaryGold, KhanaRadii.sm)
                                            .clip(KhanaRadii.sm)
                                            .height(28.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable { billingViewModel.removeFromCart(cartItem.item, cartItem.variant) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Decrease",
                                                tint = DarkBrown1,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "${cartItem.quantity}",
                                            color = DarkBrown1,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable { billingViewModel.addToCart(cartItem.item, cartItem.variant) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Increase",
                                                tint = DarkBrown1,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text(
                                        CurrencyUtils.formatPrice((cartItem.variant?.price ?: cartItem.item.basePrice).toDoubleOrNull()?.times(cartItem.quantity) ?: 0.0),
                                        color = TextLight,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1
                                    )
                                    IconButton(onClick = { showNoteDialog = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            if (cartItem.note.isNotBlank()) Icons.Default.EditNote else Icons.AutoMirrored.Filled.NoteAdd,
                                            contentDescription = "Add note",
                                            tint = if (cartItem.note.isNotBlank()) PrimaryGold else TextGold.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
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
                                            if (isSavingTable) return@Button
                                            isSavingTable = true
                                            scope.launch {
                                                val billId = billingViewModel.editingBillId ?: run { isSavingTable = false; return@launch }
                                                val ok = billingViewModel.appendItemsToDraft(billId)
                                                isSavingTable = false
                                                if (ok) {
                                                    onShowMessage("Table updated")
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
                                        enabled = derivedItemCount > 0 && !isSavingTable
                                    ) {
                                        Text("Update & Send KOT", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(modifier = Modifier.height(spacing.small))
                                } else {
                                    Button(
                                        onClick = {
                                            if (isSavingTable) return@Button
                                            isSavingTable = true
                                            scope.launch {
                                                val ok = billingViewModel.saveDraftOrder(billingViewModel.customerName.value)
                                                isSavingTable = false
                                                if (ok) {
                                                    onShowMessage("Table saved")
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
                                        enabled = derivedItemCount > 0 && !isSavingTable
                                    ) {
                                        Text("Send to Kitchen (KOT)", color = Color.White, style = MaterialTheme.typography.titleMedium)
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
                                        if (billingViewModel.editingBillId != null) "Proceed to Settle" else "Proceed to Pay",
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
    cartItems: List<BillingViewModel.CartItem> = emptyList(),
    onDismiss: () -> Unit,
    onAddVariant: (ItemVariantEntity) -> Unit,
    onRemoveVariant: (ItemVariantEntity) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val totalVariantsInCart = cartItems.sumOf { it.quantity }

    KhanaBookDialog(
        title = "Choose Variant",
        message = itemName,
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                variants.forEach { variant ->
                    val variantQty = cartItems.find { it.variant?.id == variant.id }?.quantity ?: 0
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (variantQty > 0) PrimaryGold.copy(alpha = 0.12f) else DarkBrown2,
                        shape = KhanaRadii.md,
                        border = BorderStroke(
                            1.dp,
                            if (variantQty > 0) PrimaryGold else BorderGold.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = variant.variantName,
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = CurrencyUtils.formatPrice(variant.price),
                                    color = PrimaryGold,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            if (variantQty == 0) {
                                Box(
                                    modifier = Modifier
                                        .background(PrimaryGold.copy(alpha = 0.15f), KhanaRadii.sm)
                                        .border(1.dp, PrimaryGold.copy(alpha = 0.7f), KhanaRadii.sm)
                                        .clip(KhanaRadii.sm)
                                        .clickable { onAddVariant(variant) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ Add",
                                        color = PrimaryGold,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(PrimaryGold, KhanaRadii.md)
                                        .clip(KhanaRadii.md)
                                        .height(34.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { onRemoveVariant(variant) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = DarkBrown1,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "$variantQty",
                                        color = DarkBrown1,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { onAddVariant(variant) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = DarkBrown1,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                shape = KhanaRadii.md
            ) {
                Text(
                    text = if (totalVariantsInCart > 0) "Done ($totalVariantsInCart in cart)" else "Done",
                    color = DarkBrown1,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    )
}

@Composable
private fun SquareMenuItemCard(
    item: MenuItemEntity,
    variants: List<ItemVariantEntity>,
    cartQuantity: Int,
    onCardClick: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val itemAvailable = item.isAvailable
    var loadFailed by remember(item.imageUrl, item.imageVersion) { mutableStateOf(false) }
    val showImage = !item.imageUrl.isNullOrBlank() && !loadFailed

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(KhanaRadii.lg)
            .clickable(enabled = itemAvailable) {
                if (variants.isNotEmpty() || cartQuantity == 0) {
                    onCardClick()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (itemAvailable) DarkBrown2 else DarkBrown2.copy(alpha = 0.5f)
        ),
        border = if (cartQuantity > 0) BorderStroke(1.5.dp, PrimaryGold) else BorderStroke(1.dp, BorderGold.copy(alpha = 0.15f)),
        shape = KhanaRadii.lg,
        elevation = CardDefaults.cardElevation(defaultElevation = if (cartQuantity > 0) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── TOP: Prominent Food Image Box ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .background(DarkBrown1),
                contentAlignment = Alignment.Center
            ) {
                if (showImage) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .memoryCacheKey("${item.imageUrl}:${item.imageVersion}")
                            .diskCacheKey("${item.imageUrl}:${item.imageVersion}")
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { loadFailed = true }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = TextGold.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Top-Start: Dietary FSSAI Badge (🟢 Veg / 🔴 Non-Veg)
                FoodTypeBadge(
                    type = item.foodType,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    size = 14.dp
                )

                // Top-End: Cart Quantity Badge if item is in cart
                if (cartQuantity > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(PrimaryGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$cartQuantity",
                            color = DarkBrown1,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }

                // Out of stock frosted scrim
                if (!itemAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Unavailable",
                            color = ErrorPink,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .background(DarkBrown1.copy(alpha = 0.85f), KhanaRadii.sm)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // ── BOTTOM: Dish Info & Action Controls ────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.smallMedium, vertical = spacing.small)
            ) {
                Text(
                    text = item.name,
                    color = if (itemAvailable) TextLight else TextLight.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (variants.isEmpty()) {
                        Text(
                            text = "₹${item.basePrice}",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        if (itemAvailable) {
                            if (cartQuantity == 0) {
                                Box(
                                    modifier = Modifier
                                        .background(PrimaryGold.copy(alpha = 0.15f), KhanaRadii.sm)
                                        .border(1.dp, PrimaryGold.copy(alpha = 0.7f), KhanaRadii.sm)
                                        .clip(KhanaRadii.sm)
                                        .clickable { onAdd() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ Add",
                                        color = PrimaryGold,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(PrimaryGold, KhanaRadii.md)
                                        .clip(KhanaRadii.md)
                                        .height(34.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { onRemove() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = DarkBrown1,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "$cartQuantity",
                                        color = DarkBrown1,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { onAdd() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = DarkBrown1,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val minPrice = variants.minOfOrNull { it.price.toDoubleOrNull() ?: 0.0 } ?: (item.basePrice.toDoubleOrNull() ?: 0.0)
                        Column {
                            Text(
                                text = CurrencyUtils.formatPrice(minPrice),
                                color = PrimaryGold,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${variants.size} sizes",
                                color = TextGold.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (itemAvailable) {
                            if (cartQuantity == 0) {
                                Box(
                                    modifier = Modifier
                                        .background(PrimaryGold.copy(alpha = 0.15f), KhanaRadii.sm)
                                        .border(1.dp, PrimaryGold.copy(alpha = 0.7f), KhanaRadii.sm)
                                        .clip(KhanaRadii.sm)
                                        .clickable { onCardClick() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Options",
                                        color = PrimaryGold,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .background(PrimaryGold, KhanaRadii.md)
                                        .clip(KhanaRadii.md)
                                        .clickable { onCardClick() }
                                        .height(34.dp)
                                        .padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Manage variants",
                                        tint = DarkBrown1,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$cartQuantity in cart",
                                        color = DarkBrown1,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
