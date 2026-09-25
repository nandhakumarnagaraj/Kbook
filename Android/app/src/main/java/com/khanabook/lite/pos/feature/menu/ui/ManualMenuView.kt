@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.feature.menu.ui
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.MenuWithVariants
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.menu.ui.MenuConfigurationTags
import com.khanabook.lite.pos.core.theme.*
import java.util.Locale
import kotlin.math.roundToInt

private data class PendingManualItemOverwrite(
    val existing: MenuWithVariants,
    val name: String,
    val price: Double,
    val foodType: String,
    val variants: List<Pair<String, Double>>,
    val photoUri: android.net.Uri? = null
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
    canWrite: Boolean,
    onCategorySelect: (Long) -> Unit,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (CategoryEntity) -> Unit,
    onAddItem: (String, Double, String, List<Pair<String, Double>>) -> Unit,
    onUpdateItem: (MenuItemEntity) -> Unit,
    onToggleAvailability: (Long, Boolean) -> Unit,
    onAddVariant: (Long, String, Double) -> Unit,
    onUpdateVariant: (com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity) -> Unit,
    onDeleteVariant: (com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity) -> Unit,
    onAddItemWithPhoto: ((String, Double, String, List<Pair<String, Double>>, android.net.Uri?) -> Unit)? = null,
    onUpdateItemPhoto: ((MenuItemEntity, android.net.Uri?, Boolean) -> Unit)? = null,
    /**
     * Quick per-row photo upload: (menuItemId, pickedImageUri). When supplied, every
     * item row shows an upload icon on the right so the owner can set or replace a
     * dish photo without opening the edit dialog.
     */
    onQuickPhotoUpload: ((Long, android.net.Uri) -> Unit)? = null,
    onDeleteItem: ((MenuItemEntity) -> Unit)? = null,
    onMoveItem: ((MenuItemEntity, Long) -> Unit)? = null,
    otherCategories: List<CategoryEntity> = emptyList(),
    onDeleteCategory: ((CategoryEntity) -> Unit)? = null,
    onAddCategoryWithSort: ((String, Int) -> Unit)? = null,
    onReorderCategories: ((List<CategoryEntity>) -> Unit)? = null,
    onToggleCategory: ((CategoryEntity, Boolean) -> Unit)? = null
) {
    val spacing = KhanaBookTheme.spacing
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<CategoryEntity?>(null) }
    var showDeleteCategoryConfirm by remember { mutableStateOf<CategoryEntity?>(null) }
    var showManageCategories by remember { mutableStateOf(false) }
    var showSelectedCategoryMenu by remember { mutableStateOf(false) }

    // Quick per-row dish photo upload. Remembers which item the picker was opened
    // for, then hands the picked image to the caller for upload.
    var pendingPhotoItemId by remember { mutableStateOf<Long?>(null) }
    val quickPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val targetId = pendingPhotoItemId
        pendingPhotoItemId = null
        if (uri != null && targetId != null) {
            onQuickPhotoUpload?.invoke(targetId, uri)
        }
    }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf<MenuWithVariants?>(null) }
    var pendingOverwrite by remember { mutableStateOf<PendingManualItemOverwrite?>(null) }
    val visibleMenuItems = menuItems

    val applyItemDraftToExisting: (MenuWithVariants, String, Double, String, List<Pair<String, Double>>, android.net.Uri?) -> Unit =
        { existingItem, updatedName, updatedPrice, updatedFoodType, updatedVariants, photoUri ->
            val updatedEntity = existingItem.menuItem.copy(
                name = updatedName.trim(),
                basePrice = updatedPrice.toString(),
                foodType = updatedFoodType,
                updatedAt = System.currentTimeMillis()
            )
            if (onUpdateItemPhoto != null) {
                onUpdateItemPhoto(updatedEntity, photoUri, false)
            } else {
                onUpdateItem(updatedEntity)
            }
            existingItem.variants.forEach { onDeleteVariant(it) }
            updatedVariants.forEach { (variantName, variantPrice) ->
                onAddVariant(existingItem.menuItem.id, variantName, variantPrice)
            }
        }

    Column(modifier = Modifier.fillMaxSize().testTag(MenuConfigurationTags.manualMenuRoot)) {
        val categoryRowState = rememberLazyListState()
        val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Menu categories",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedCategory?.let {
                        val itemLabel = if (visibleMenuItems.size == 1) "1 item" else "${visibleMenuItems.size} items"
                        "${it.name} · $itemLabel"
                    }
                        ?: "Select a category",
                    color = TextGold.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canWrite) {
                if (selectedCategory != null) {
                    Box {
                        IconButton(onClick = { showSelectedCategoryMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Actions for ${selectedCategory.name}", tint = TextGold)
                        }
                        DropdownMenu(
                            expanded = showSelectedCategoryMenu,
                            onDismissRequest = { showSelectedCategoryMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Category") },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextGold) },
                                onClick = { showSelectedCategoryMenu = false; showEditCategoryDialog = selectedCategory }
                            )
                            if (onToggleCategory != null) {
                                DropdownMenuItem(
                                    text = { Text(if (selectedCategory.isActive) "Hide from billing" else "Show in billing") },
                                    leadingIcon = { Icon(if (selectedCategory.isActive) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextGold) },
                                    onClick = {
                                        showSelectedCategoryMenu = false
                                        onToggleCategory(selectedCategory, !selectedCategory.isActive)
                                    }
                                )
                            }
                            if (onDeleteCategory != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete Category") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = NonVegRed) },
                                    onClick = { showSelectedCategoryMenu = false; showDeleteCategoryConfirm = selectedCategory }
                                )
                            }
                            if (onReorderCategories != null) {
                                DropdownMenuItem(
                                    text = { Text("Manage Categories") },
                                    leadingIcon = { Icon(Icons.Default.Tune, null, tint = TextGold) },
                                    onClick = { showSelectedCategoryMenu = false; showManageCategories = true }
                                )
                            }
                        }
                    }
                }
                KhanaSecondaryButton(
                    text = "Add Category",
                    onClick = { showAddCategoryDialog = true },
                    leadingIcon = Icons.Default.Add,
                    modifier = Modifier.testTag(MenuConfigurationTags.addCategoryButton)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.extraSmall)
        ) {
            LazyRow(
                state = categoryRowState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories) { category ->
                    val isSelected = category.id == selectedCategoryId
                    Surface(
                        onClick = { onCategorySelect(category.id) },
                        shape = KhanaRadii.md,
                        color = (if (isSelected) PrimaryGold else DarkBrown2)
                            .copy(alpha = if (category.isActive) 1f else 0.5f),
                        border = BorderStroke(1.dp, (if (isSelected) PrimaryGold else BorderGold.copy(alpha = 0.3f))
                            .copy(alpha = if (category.isActive) 1f else 0.5f)),
                        contentColor = if (isSelected) DarkBrown1 else TextLight
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .heightIn(min = 36.dp)
                                .padding(horizontal = spacing.smallMedium, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            HorizontalChipScrollbar(
                state = categoryRowState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.small)
            )
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
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        if (canWrite)
                            "Tap + above to create your first category,\nthen add your menu items."
                        else
                            "The restaurant owner or an admin\nmanages the menu on this device.",
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
                    .padding(horizontal = spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                contentPadding = PaddingValues(top = spacing.small, bottom = spacing.bottomListPadding)
            ) {
                if (visibleMenuItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = spacing.huge),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Text(
                                    "No items in this category",
                                    color = TextLight,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    if (canWrite)
                                        "Tap \"Add New Item\" below to get started."
                                    else
                                        "The restaurant owner or an admin edits the menu.",
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
                            canWrite = canWrite,
                            onToggleAvailability = onToggleAvailability,
                            onEditClick = { showEditItemDialog = it },
                            onDeleteItem = onDeleteItem,
                            onMoveItem = onMoveItem,
                            otherCategories = otherCategories,
                            onUploadPhotoClick = if (onQuickPhotoUpload != null) {
                                {
                                    pendingPhotoItemId = itemWithVariants.menuItem.id
                                    quickPhotoPickerLauncher.launch("image/*")
                                }
                            } else null
                        )
                    }
                }

            }
            
            // Fixed Footer
            Surface(
                color = DarkBrown1, // Match background to merge seamlessly
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (canWrite) {
                        Text(
                            "Tap an item to edit · Only available items appear on bills",
                            color = TextGold.copy(alpha = 0.75f),
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
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text("Add New Item", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "Viewing menu in read-only mode.\nOnly the restaurant owner or an admin can edit the menu.",
                            color = TextGold.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
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
                        pending.variants,
                        pending.photoUri
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

    showDeleteCategoryConfirm?.let { cat ->
        val itemCount = menuItems.count { it.menuItem.categoryId == cat.id }
        KhanaBookDialog(
            onDismissRequest = { showDeleteCategoryConfirm = null },
            title = "Delete Category",
            message = if (itemCount > 0)
                "Delete \"${cat.name}\" and all $itemCount item(s) inside it? This cannot be undone."
            else
                "Delete \"${cat.name}\"? This cannot be undone."
        ) {
            TextButton(onClick = { showDeleteCategoryConfirm = null }) {
                Text("Cancel", color = TextGold)
            }
            TextButton(
                onClick = {
                    showDeleteCategoryConfirm?.let { onDeleteCategory?.invoke(it) }
                    showDeleteCategoryConfirm = null
                }
            ) {
                Text("Delete", color = NonVegRed)
            }
        }
    }

    if (showManageCategories && canWrite) {
        ManageCategoriesDialog(
            categories = categories,
            itemCounts = menuItems.groupBy { it.menuItem.categoryId }.mapValues { it.value.size },
            canWrite = canWrite,
            onAddCategory = { name, sortOrder ->
                if (onAddCategoryWithSort != null) {
                    onAddCategoryWithSort(name, sortOrder)
                } else {
                    onAddCategory(name)
                }
                showManageCategories = false
            },
            onRenameCategory = { category ->
                showManageCategories = false
                showEditCategoryDialog = category
            },
            onDeleteCategory = { category ->
                showManageCategories = false
                showDeleteCategoryConfirm = category
            },
            onReorderCategories = { ordered ->
                onReorderCategories?.invoke(ordered)
                showManageCategories = false
            },
            onDismiss = { showManageCategories = false }
        )
    }

    // Item Dialogs
    if (showAddItemDialog) {
        ItemEditDialog(
            title = "Add New Item",
            onDismiss = { showAddItemDialog = false },
            onConfirmWithPhoto = { name, price, type, draftVariants, photoUri, _ ->
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
                        variants = draftVariants,
                        photoUri = photoUri
                    )
                } else {
                    if (onAddItemWithPhoto != null) {
                        onAddItemWithPhoto(name.trim(), price, type, draftVariants, photoUri)
                    } else {
                        onAddItem(name.trim(), price, type, draftVariants)
                    }
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
            initialImageUrl = itemWithVariants.menuItem.imageUrl,
            variants = itemWithVariants.variants,
            onDismiss = { showEditItemDialog = null },
            onConfirmWithPhoto = { name, price, type, updatedVariants, photoUri, removePhoto ->
                val updatedEntity = itemWithVariants.menuItem.copy(
                    name = name.trim(),
                    basePrice = price.toString(),
                    foodType = type,
                    updatedAt = System.currentTimeMillis()
                )
                if (onUpdateItemPhoto != null) {
                    onUpdateItemPhoto(updatedEntity, photoUri, removePhoto)
                } else {
                    onUpdateItem(updatedEntity)
                }
                itemWithVariants.variants.forEach { onDeleteVariant(it) }
                updatedVariants.forEach { (variantName, variantPrice) ->
                    onAddVariant(itemWithVariants.menuItem.id, variantName, variantPrice)
                }
                showEditItemDialog = null
            }
        )
    }

}

@Composable
fun MenuItemRow(
    itemWithVariants: MenuWithVariants,
    canWrite: Boolean = true,
    onToggleAvailability: (Long, Boolean) -> Unit,
    onEditClick: (MenuWithVariants) -> Unit,
    onUploadPhotoClick: (() -> Unit)? = null,
    onDeleteItem: ((MenuItemEntity) -> Unit)? = null,
    onMoveItem: ((MenuItemEntity, Long) -> Unit)? = null,
    otherCategories: List<CategoryEntity> = emptyList()
) {
    val item = itemWithVariants.menuItem
    val variants = itemWithVariants.variants
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var showMoveCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canWrite) {
                    Modifier.combinedClickable(
                        onClick = { onEditClick(itemWithVariants) },
                        onLongClick = { onEditClick(itemWithVariants) }
                    )
                } else {
                    Modifier
                }
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
            MenuItemThumbnail(
                imageUrl = item.imageUrl,
                imageVersion = item.imageVersion,
                foodType = item.foodType,
                size = 56.dp,
                showAddPhotoHint = canWrite
            )
            Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.smallMedium))
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
                        color = TextGold,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        text = "₹${item.basePrice.toDoubleOrNull()?.toInt() ?: item.basePrice}",
                        color = TextGold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (canWrite) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.extraSmall)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = item.isAvailable,
                            onCheckedChange = { onToggleAvailability(item.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BrownSelected,
                                checkedTrackColor = PrimaryGold,
                                checkedBorderColor = PrimaryGold,
                                uncheckedThumbColor = TextLight,
                                uncheckedTrackColor = DarkBrown1,
                                uncheckedBorderColor = BorderGold
                            )
                        )
                        Text(
                            text = if (item.isAvailable) "Available" else "Unavailable",
                            color = if (item.isAvailable) VegGreen else NonVegRed,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (onUploadPhotoClick != null && item.imageUrl.isNullOrBlank()) {
                        IconButton(onClick = onUploadPhotoClick) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Upload photo for ${item.name}",
                                tint = PrimaryGold.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More actions for ${item.name}",
                                tint = TextGold
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextGold) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onEditClick(itemWithVariants)
                                }
                            )
                            if (otherCategories.isNotEmpty() && onMoveItem != null) {
                                DropdownMenuItem(
                                    text = { Text("Move to Category") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, tint = TextGold) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        showMoveCategoryDialog = true
                                    }
                                )
                            }
                            if (onDeleteItem != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = NonVegRed) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = KhanaRadii.pill,
                    color = if (item.isAvailable) VegGreen.copy(alpha = 0.15f) else NonVegRed.copy(alpha = 0.15f)
                ) {
Text(
                    text = if (item.isAvailable) "Available" else "Unavailable",
                    color = if (item.isAvailable) VegGreen else NonVegRed,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = KhanaBookTheme.spacing.small, vertical = KhanaBookTheme.spacing.extraSmall)
                )
                }
            }
        }
    }

    if (showMoveCategoryDialog) {
        KhanaBookDialog(
            onDismissRequest = { showMoveCategoryDialog = false },
            title = "Move to Category",
            content = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    otherCategories.forEach { category ->
                        TextButton(
                            onClick = {
                                onMoveItem?.invoke(item, category.id)
                                showMoveCategoryDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(category.name, color = TextLight)
                        }
                    }
                }
            }
        ) {
            TextButton(onClick = { showMoveCategoryDialog = false }) {
                Text("Cancel", color = TextGold)
            }
        }
    }

    if (showDeleteConfirmDialog) {
        KhanaBookDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = "Delete Item",
            message = "Remove \"${item.name}\" from the menu? This cannot be undone."
        ) {
            TextButton(onClick = { showDeleteConfirmDialog = false }) {
                Text("Cancel", color = TextGold)
            }
            TextButton(onClick = {
                onDeleteItem?.invoke(item)
                showDeleteConfirmDialog = false
            }) {
                Text("Delete", color = NonVegRed)
            }
        }
    }
}

@Composable
private fun HorizontalChipScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = state.layoutInfo
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(0)
    val items = layoutInfo.visibleItemsInfo
    val firstStart = items.firstOrNull()?.offset ?: 0
    val lastEnd = items.lastOrNull()?.let { it.offset + it.size } ?: viewport
    val seenCount = items.size.coerceAtLeast(1)
    val avgItemExtent = if (seenCount > 1) {
        ((lastEnd - firstStart) / seenCount.toFloat()).coerceAtLeast(0f)
    } else {
        0f
    }
    val totalItems = layoutInfo.totalItemsCount.coerceAtLeast(1)
    val contentLength = (avgItemExtent * totalItems).coerceAtLeast(viewport.toFloat())
    val scrollRange = contentLength - viewport
    val isScrollable = scrollRange > 1f

    Box(
        modifier = modifier
            .height(3.dp)
            .clip(CircleShape)
            .background(BorderGold.copy(alpha = 0.18f))
    ) {
        if (isScrollable) {
            val thumbFraction = (viewport.toFloat() / contentLength).coerceIn(0.12f, 1f)
            val travel = 1f - thumbFraction
            val scrollFraction = (layoutInfo.viewportStartOffset.toFloat() / scrollRange).coerceIn(0f, 1f)
            val fullWidth = layoutInfo.viewportSize.width
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(thumbFraction)
                    .offset { IntOffset((fullWidth * travel * scrollFraction).roundToInt(), 0) }
                    .clip(CircleShape)
                    .background(PrimaryGold.copy(alpha = 0.85f))
            )
        }
    }
}
