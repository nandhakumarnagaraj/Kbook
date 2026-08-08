@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens.activeorder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel.CartItem
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import java.math.BigDecimal

@Composable
fun BillInfoHeader(
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
        shape = KhanaRadii.lg
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
fun ActiveOrderItemCard(
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
            modifier = Modifier.background(PrimaryGold, KhanaRadii.sm).height(28.dp)
        ) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Remove, null, tint = DarkBrown1, modifier = Modifier.size(KhanaBookTheme.iconSize.xsmall))
            }
            Text("${cartItem.quantity}", color = DarkBrown1, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            IconButton(onClick = onIncrement, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, null, tint = DarkBrown1, modifier = Modifier.size(KhanaBookTheme.iconSize.xsmall))
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
fun CompactMenuSection(
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
        shape = KhanaRadii.lg
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
                    shape = KhanaRadii.md
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
                                                modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
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

@Composable private fun menuTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextLight,
    unfocusedTextColor = TextLight,
    cursorColor = PrimaryGold,
    focusedBorderColor = PrimaryGold,
    unfocusedBorderColor = BorderGold.copy(alpha = 0.3f),
    focusedLabelColor = PrimaryGold,
    unfocusedLabelColor = TextGold
)
