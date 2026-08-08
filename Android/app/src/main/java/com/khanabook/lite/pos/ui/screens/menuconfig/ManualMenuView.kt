@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.ui.screens.menuconfig

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.screens.MenuConfigurationTags
import com.khanabook.lite.pos.ui.theme.*
import java.util.Locale

private data class PendingManualItemOverwrite(
    val existing: MenuWithVariants,
    val name: String,
    val price: Double,
    val foodType: String,
    val variants: List<Pair<String, Double>>
)

internal data class EditableVariantDraft(
    val name: String,
    val price: Double
)

private fun normalizeMenuItemName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.getDefault())


@Composable
fun ManualMenuView(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    menuItems: List<MenuWithVariants>,
    onCategorySelect: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onAddItem: (String, Double, String, List<Pair<String, Double>>) -> Unit,
    onUpdateItem: (MenuItemEntity) -> Unit,
    onToggleAvailability: (Long, Boolean) -> Unit,
    onAddVariant: (Long, String, Double) -> Unit,
    onUpdateVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit,
    onDeleteVariant: (com.khanabook.lite.pos.data.local.entity.ItemVariantEntity) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<CategoryEntity?>(null) }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf<MenuWithVariants?>(null) }
    var pendingOverwrite by remember { mutableStateOf<PendingManualItemOverwrite?>(null) }
    val visibleMenuItems = menuItems

    val applyItemDraftToExisting: (MenuWithVariants, String, Double, String, List<Pair<String, Double>>) -> Unit =
        { existingItem, updatedName, updatedPrice, updatedFoodType, updatedVariants ->
            onUpdateItem(
                existingItem.menuItem.copy(
                    name = updatedName.trim(),
                    basePrice = updatedPrice.toString(),
                    foodType = updatedFoodType,
                    updatedAt = System.currentTimeMillis()
                )
            )
            existingItem.variants.forEach { onDeleteVariant(it) }
            updatedVariants.forEach { (variantName, variantPrice) ->
                onAddVariant(existingItem.menuItem.id, variantName, variantPrice)
            }
        }

    Column(modifier = Modifier.fillMaxSize().testTag(MenuConfigurationTags.manualMenuRoot)) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { category ->
                val isSelected = category.id == selectedCategoryId
                Surface(
                    onClick = { onCategorySelect(category.id) },
                    shape = KhanaRadii.md,
                    color = if (isSelected) PrimaryGold else DarkBrown2,
                    border = BorderStroke(1.dp, if (isSelected) PrimaryGold else BorderGold.copy(alpha = 0.3f)),
                    contentColor = if (isSelected) DarkBrown1 else TextLight
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .combinedClickable(
                                onClick = { onCategorySelect(category.id) },
                                onLongClick = { showEditCategoryDialog = category }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSelected && visibleMenuItems.isNotEmpty()) "${category.name} (${visibleMenuItems.size})" else category.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Category",
                                modifier = Modifier
                                    .size(KhanaBookTheme.iconSize.xsmall)
                                    .clickable { showEditCategoryDialog = category },
                                tint = DarkBrown1
                            )
                        }
                    }
                }
            }
            item {
                KhanaSecondaryButton(
                    text = "Add Category",
                    onClick = { showAddCategoryDialog = true },
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier.testTag(MenuConfigurationTags.addCategoryButton)
                )
            }
        }

        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(KhanaBookTheme.spacing.extraLarge)
                ) {
                    Box(
                        modifier = Modifier
                            .size(KhanaBookTheme.iconSize.heroCircle)
                            .background(PrimaryGold.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, PrimaryGold.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.large))
                    }
                    Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.medium))
                    Text(
                        "No categories yet",
                        color = TextLight,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap + above to create your first category,\nthen add your menu items.",
                        color = TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = spacing.bottomListPadding)
            ) {
                if (visibleMenuItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "No items in this category",
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "Tap \"Add New Item\" below to get started.",
                                    color = TextGold.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = visibleMenuItems,
                        key = { it.menuItem.id }
                    ) { itemWithVariants ->
                        MenuItemRow(
                            itemWithVariants = itemWithVariants,
                            onToggleAvailability = onToggleAvailability,
                            onEditClick = { showEditItemDialog = it }
                        )
                    }
                }

            }
            
            // Fixed Footer Button
            Surface(
                color = DarkBrown1, // Match background to merge seamlessly
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Tap to edit  •  Toggle switch to enable / disable",
                        color = TextGold.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    Button(
                        onClick = { showAddItemDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(MenuConfigurationTags.addItemButton),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGold,
                            contentColor = DarkBrown1
                        ),
                        enabled = selectedCategoryId != null,
                        shape = KhanaRadii.lg
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New Item", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    pendingOverwrite?.let { pending ->
        KhanaBookDialog(
            onDismissRequest = { pendingOverwrite = null },
            title = "Item already exists",
            message = "An item with this name already exists in this category. Do you want to overwrite/update the existing item?"
        ) {
            TextButton(onClick = { pendingOverwrite = null }) {
                Text("Cancel", color = TextGold)
            }
            TextButton(
                onClick = {
                    applyItemDraftToExisting(
                        pending.existing,
                        pending.name,
                        pending.price,
                        pending.foodType,
                        pending.variants
                    )
                    pendingOverwrite = null
                }
            ) {
                Text("Overwrite", color = PrimaryGold)
            }
        }
    }

    // Category Dialogs
    if (showAddCategoryDialog) {
        CategoryEditDialog(
            title = "Add Category",
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name ->
                onAddCategory(name)
                showAddCategoryDialog = false
            }
        )
    }

    if (showEditCategoryDialog != null) {
        CategoryEditDialog(
            title = "Edit Category",
            initialName = showEditCategoryDialog?.name ?: "",
            onDismiss = { showEditCategoryDialog = null },
            onConfirm = { name ->
                showEditCategoryDialog?.let {
                    onUpdateCategory(it.copy(name = name))
                }
                showEditCategoryDialog = null
            }
        )
    }

    // Item Dialogs
    if (showAddItemDialog) {
        ItemEditDialog(
            title = "Add New Item",
            onDismiss = { showAddItemDialog = false },
            onConfirm = { name, price, type, draftVariants ->
                val normalizedName = normalizeMenuItemName(name)
                val existing = visibleMenuItems.firstOrNull {
                    normalizeMenuItemName(it.menuItem.name) == normalizedName
                }
                if (existing != null) {
                    pendingOverwrite = PendingManualItemOverwrite(
                        existing = existing,
                        name = name.trim(),
                        price = price,
                        foodType = type,
                        variants = draftVariants
                    )
                } else {
                    onAddItem(name.trim(), price, type, draftVariants)
                }
                showAddItemDialog = false
            }
        )
    }

    showEditItemDialog?.let { itemWithVariants ->
        ItemEditDialog(
            title = "Edit Item",
            initialName = itemWithVariants.menuItem.name,
            initialPrice = itemWithVariants.menuItem.basePrice.toDoubleOrNull() ?: 0.0,
            initialType = itemWithVariants.menuItem.foodType,
            variants = itemWithVariants.variants,
            onDismiss = { showEditItemDialog = null },
            onConfirm = { name, price, type, updatedVariants ->
                applyItemDraftToExisting(itemWithVariants, name, price, type, updatedVariants)
                showEditItemDialog = null
            }
        )
    }

}

@Composable
fun MenuItemRow(
    itemWithVariants: MenuWithVariants,
    onToggleAvailability: (Long, Boolean) -> Unit,
    onEditClick: (MenuWithVariants) -> Unit
) {
    val item = itemWithVariants.menuItem
    val variants = itemWithVariants.variants

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEditClick(itemWithVariants) },
                onLongClick = { onEditClick(itemWithVariants) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isAvailable) DarkBrown2 else DarkBrown2.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            0.5.dp,
            if (item.isAvailable) BorderGold.copy(alpha = 0.2f) else BorderGold.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(KhanaBookTheme.spacing.smallMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                tint = if (item.foodType == "veg") VegGreen else NonVegRed,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = if (item.isAvailable) TextLight else TextLight.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (variants.isNotEmpty()) {
                    Text(
                        text = "${variants.size} variants • Starts from ₹${variants.minOf { it.price.toDoubleOrNull() ?: 0.0 }.toInt()}",
                        color = TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        text = "₹${item.basePrice.toDoubleOrNull()?.toInt() ?: item.basePrice}",
                        color = TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            KhanaBookSwitch(
                checked = item.isAvailable,
                onCheckedChange = { onToggleAvailability(item.id, it) },
                checkedTrackColor = PrimaryGold,
                checkedThumbColor = BrownSelected
            )
        }
    }
}
