@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.khanabook.lite.pos.ui.screens.menuconfig

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.screens.MenuConfigurationTags
import com.khanabook.lite.pos.ui.screens.ReviewSheetLayout
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import kotlinx.coroutines.launch


@Composable
fun ReviewDetectedItemsScreen(
    drafts: List<MenuViewModel.DraftMenuItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    showOverwritePrompt: Boolean,
    onDismissOverwritePrompt: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onUpdateDraft: (Int, MenuViewModel.DraftMenuItem) -> Unit,
    onToggleFoodType: (Int) -> Unit
) {
    val selectedCount = drafts.count { it.isSelected }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    if (drafts.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBrown1)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = KhanaBookTheme.spacing.smallMedium)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(PrimaryGold.copy(alpha = 0.4f), CircleShape)
                    )
                }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = KhanaBookTheme.spacing.mediumLarge, vertical = KhanaBookTheme.spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Review Detected Items",
                                    color = PrimaryGold,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${drafts.size} items found · $selectedCount selected",
                                    color = TextGold.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextGold)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ReviewSheetLayout.HORIZONTAL_PADDING + ReviewSheetLayout.CARD_PADDING, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_WIDTH + ReviewSheetLayout.CHECKBOX_GAP))
                            Text("Item Name", color = TextGold.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.small))
                            Text("Price", color = TextGold.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(ReviewSheetLayout.PRICE_WIDTH))
                            Spacer(modifier = Modifier.width(ReviewSheetLayout.FOOD_ICON_WIDTH))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.smallMedium),
                        contentPadding = PaddingValues(horizontal = ReviewSheetLayout.HORIZONTAL_PADDING, vertical = KhanaBookTheme.spacing.smallMedium)
                    ) {
                        val groupedDrafts = drafts.withIndex().groupBy { it.value.categoryName ?: "Uncategorized" }

                        groupedDrafts.forEach { (categoryName, indexedItems) ->
                            val allInCategorySelected = indexedItems.all { it.value.isSelected }

                            item(key = "header_$categoryName") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = ReviewSheetLayout.CARD_PADDING, vertical = KhanaBookTheme.spacing.small),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(ReviewSheetLayout.CHECKBOX_WIDTH)
                                            .clip(KhanaRadii.sm)
                                            .background(
                                                if (allInCategorySelected) PrimaryGold else Color.Transparent
                                            )
                                            .border(
                                                1.5.dp,
                                                if (allInCategorySelected) PrimaryGold else TextGold.copy(alpha = 0.5f),
                                                KhanaRadii.sm
                                            )
                                            .clickable {
                                                val targetSelection = !allInCategorySelected
                                                indexedItems.forEach { indexed ->
                                                    if (indexed.value.isSelected != targetSelection) {
                                                        onToggleSelection(indexed.index)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (allInCategorySelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = DarkBrown1,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_GAP))

                                    Text(
                                        categoryName.uppercase(),
                                        color = PrimaryGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            items(indexedItems.size) { i ->
                                val index = indexedItems[i].index
                                val draft = indexedItems[i].value

                                DraftItemRow(
                                    index = index,
                                    draft = draft,
                                    onToggleSelection = { onToggleSelection(index) },
                                    onUpdateDraft = { onUpdateDraft(index, it) },
                                    onToggleFoodType = { onToggleFoodType(index) }
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkBrown2,
                        border = BorderStroke(0.5.dp, BorderGold.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.smallMedium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showDiscardConfirm = true },
                                border = BorderStroke(1.5.dp, NonVegRed.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NonVegRed),
                                modifier = Modifier.weight(1f).height(KhanaBookTheme.spacing.buttonHeightLarge),
                                shape = KhanaRadii.lg
                            ) {
                                Text("Discard", maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onConfirm,
                                enabled = selectedCount > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryGold,
                                    contentColor = DarkBrown1
                                ),
                                modifier = Modifier.weight(2f).height(KhanaBookTheme.spacing.buttonHeightLarge),
                                shape = KhanaRadii.lg
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null, modifier = Modifier.size(KhanaBookTheme.iconSize.medium))
                                Spacer(Modifier.width(KhanaBookTheme.spacing.small))
                                Text(
                                    "Add $selectedCount Items",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }


@Composable
internal fun ReviewDetectedItemsOverlay(
    drafts: List<MenuViewModel.DraftMenuItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    showOverwritePrompt: Boolean,
    onDismissOverwritePrompt: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onUpdateDraft: (Int, MenuViewModel.DraftMenuItem) -> Unit,
    onToggleFoodType: (Int) -> Unit
) {
    if (drafts.isEmpty()) return

    val selectedCount = drafts.count { it.isSelected }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val dismissInteractionSource = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize().testTag(MenuConfigurationTags.reviewOverlayRoot)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .testTag(MenuConfigurationTags.reviewOverlayBackground)
                .background(DarkBrown1.copy(alpha = 0.82f))
                .clickable(
                    interactionSource = dismissInteractionSource,
                    indication = null,
                    onClick = {
                        onDismissOverwritePrompt()
                        showDiscardConfirm = false
                        onDismiss()
                    }
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f)
                .padding(bottom = 10.dp, top = 10.dp)
                .imePadding()
                .testTag(MenuConfigurationTags.reviewOverlaySheet),
            color = DarkBrown1,
            shape = KhanaRadii.pill,
            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = KhanaBookTheme.spacing.smallMedium),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(PrimaryGold.copy(alpha = 0.4f), CircleShape)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = KhanaBookTheme.spacing.mediumLarge, vertical = KhanaBookTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Review Detected Items",
                            color = PrimaryGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${drafts.size} items found · $selectedCount selected",
                            color = TextGold.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = {
                        onDismissOverwritePrompt()
                        showDiscardConfirm = false
                        onDismiss()
                    }, modifier = Modifier.testTag(MenuConfigurationTags.reviewOverlayClose)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextGold)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ReviewSheetLayout.HORIZONTAL_PADDING + ReviewSheetLayout.CARD_PADDING,
                            vertical = 4.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_WIDTH + ReviewSheetLayout.CHECKBOX_GAP))
                    Text("Type", color = TextGold.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.width(ReviewSheetLayout.FOOD_ICON_WIDTH))
                    Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.smallMedium))
                    Text("Item Name", color = TextGold.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.extraSmall))
                    Text(
                        "Price",
                        color = TextGold.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(ReviewSheetLayout.PRICE_WIDTH)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small),
                    contentPadding = PaddingValues(horizontal = ReviewSheetLayout.HORIZONTAL_PADDING, vertical = KhanaBookTheme.spacing.small)
                ) {
                    val groupedDrafts = drafts.withIndex().groupBy { it.value.categoryName ?: "Uncategorized" }

                    groupedDrafts.forEach { (categoryName, indexedItems) ->
                        val allInCategorySelected = indexedItems.all { it.value.isSelected }

                        item(key = "header_$categoryName") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ReviewSheetLayout.CARD_PADDING, vertical = KhanaBookTheme.spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(ReviewSheetLayout.CHECKBOX_WIDTH)
                                        .clip(KhanaRadii.sm)
                                        .background(if (allInCategorySelected) PrimaryGold else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (allInCategorySelected) PrimaryGold else TextGold.copy(alpha = 0.5f),
                                            KhanaRadii.sm
                                        )
                                        .clickable {
                                            val targetSelection = !allInCategorySelected
                                            indexedItems.forEach { indexed ->
                                                if (indexed.value.isSelected != targetSelection) {
                                                    onToggleSelection(indexed.index)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (allInCategorySelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = DarkBrown1,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_GAP))

                                Text(
                                    categoryName.uppercase(),
                                    color = PrimaryGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        items(indexedItems.size) { i ->
                            val index = indexedItems[i].index
                            val draft = indexedItems[i].value

                            DraftItemRow(
                                index = index,
                                draft = draft,
                                onToggleSelection = { onToggleSelection(index) },
                                onUpdateDraft = { onUpdateDraft(index, it) },
                                onToggleFoodType = { onToggleFoodType(index) }
                            )
                        }
                    }
                }

                if (showDiscardConfirm) {
                    InlineDecisionBar(
                        title = "Discard Items?",
                        message = "All ${drafts.size} detected items will be discarded.",
                        primaryLabel = "Discard",
                        primaryColor = NonVegRed,
                        secondaryLabel = "Keep Editing",
                        secondaryColor = PrimaryGold,
                        onPrimaryClick = {
                            showDiscardConfirm = false
                            onDismiss()
                        },
                        onSecondaryClick = { showDiscardConfirm = false }
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkBrown2,
                    border = BorderStroke(0.5.dp, BorderGold.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KhanaBookTheme.spacing.mediumLarge, vertical = KhanaBookTheme.spacing.smallMedium),
                        horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.smallMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismissOverwritePrompt()
                                showDiscardConfirm = true
                            },
                            border = BorderStroke(1.5.dp, NonVegRed.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NonVegRed),
                            modifier = Modifier
                                .weight(1f)
                                .height(KhanaBookTheme.spacing.buttonHeightLarge)
                                .testTag(MenuConfigurationTags.reviewOverlayDiscard),
                            shape = KhanaRadii.lg
                        ) {
                            Text(
                                if (showDiscardConfirm) "Confirming..." else "Discard",
                                maxLines = 1,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                showDiscardConfirm = false
                                onConfirm()
                            },
                            enabled = selectedCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGold,
                                contentColor = DarkBrown1
                            ),
                            modifier = Modifier
                                .weight(2f)
                                .height(KhanaBookTheme.spacing.buttonHeightLarge)
                                .testTag(MenuConfigurationTags.reviewOverlayConfirm),
                            shape = KhanaRadii.lg
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, null, modifier = Modifier.size(KhanaBookTheme.iconSize.medium))
                            Spacer(Modifier.width(KhanaBookTheme.spacing.small))
                            Text(
                                "Add $selectedCount Items",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        if (showOverwritePrompt) {
            ConflictResolutionDialog(
                onOverwriteAll = onConfirmOverwrite,
                onMergeAndSkip = {
                    onDismissOverwritePrompt()
                    onConfirm()
                },
                onCancel = onDismissOverwritePrompt
            )
        }
    }
}


@Composable
private fun InlineDecisionBar(
    title: String,
    message: String,
    primaryLabel: String,
    primaryColor: Color,
    secondaryLabel: String,
    secondaryColor: Color,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    tertiaryLabel: String? = null,
    onTertiaryClick: (() -> Unit)? = null,
    primaryTag: String? = null,
    secondaryTag: String? = null,
    tertiaryTag: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkBrown1.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KhanaBookTheme.spacing.mediumLarge, vertical = KhanaBookTheme.spacing.smallMedium),
            verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small)
        ) {
            Text(title, color = PrimaryGold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(message, color = TextLight, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrimaryClick,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = DarkBrown1),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (primaryTag != null) Modifier.testTag(primaryTag) else Modifier)
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSecondaryClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryColor),
                    border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .weight(1f)
                        .then(if (secondaryTag != null) Modifier.testTag(secondaryTag) else Modifier)
                ) {
                    Text(secondaryLabel, fontWeight = FontWeight.Bold)
                }
                if (tertiaryLabel != null && onTertiaryClick != null) {
                    TextButton(
                        onClick = onTertiaryClick,
                        modifier = if (tertiaryTag != null) Modifier.testTag(tertiaryTag) else Modifier
                    ) {
                        Text(tertiaryLabel, color = TextGold)
                    }
                }
            }
        }
    }
}


@Composable
private fun ConflictResolutionDialog(
    onOverwriteAll: () -> Unit,
    onMergeAndSkip: () -> Unit,
    onCancel: () -> Unit
) {
    KhanaBookDialog(
        onDismissRequest = onCancel,
        title = "Conflicts Found",
        message = "Some selected items already exist in this category. Choose how to continue."
    ) {
        Button(
            onClick = onOverwriteAll,
            colors = ButtonDefaults.buttonColors(
                containerColor = NonVegRed,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(KhanaBookTheme.spacing.buttonHeight)
                .testTag(MenuConfigurationTags.reviewOverlayConflictOverwrite),
            shape = KhanaRadii.lg
        ) {
            Text("Overwrite All", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        OutlinedButton(
            onClick = onMergeAndSkip,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen),
            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(KhanaBookTheme.spacing.buttonHeight)
                .testTag(MenuConfigurationTags.reviewOverlayConflictMerge),
            shape = KhanaRadii.lg
        ) {
            Text("Merge & Skip", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGold),
            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(KhanaBookTheme.spacing.buttonHeightCompact)
                .testTag(MenuConfigurationTags.reviewOverlayConflictCancel),
            shape = KhanaRadii.lg
        ) {
            Text("Cancel", fontWeight = FontWeight.Medium)
        }
    }
}


@Composable
fun DraftItemRow(
    index: Int,
    draft: MenuViewModel.DraftMenuItem,
    onToggleSelection: () -> Unit,
    onUpdateDraft: (MenuViewModel.DraftMenuItem) -> Unit,
    onToggleFoodType: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (draft.isSelected) DarkBrown2 else Color.Transparent,
        animationSpec = tween(200),
        label = "item_bg"
    )

    val rowRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(rowRequester)
            .clip(KhanaRadii.lg)
            .background(bgColor)
            .border(
                width = 0.5.dp,
                color = if (draft.isSelected) BorderGold else BorderGold.copy(alpha = 0.15f),
                shape = KhanaRadii.lg
            )
            .clickable { onToggleSelection() }
            .padding(horizontal = ReviewSheetLayout.CARD_PADDING, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(ReviewSheetLayout.CHECKBOX_WIDTH)
                    .clip(KhanaRadii.sm)
                    .background(
                        if (draft.isSelected) PrimaryGold else Color.Transparent
                    )
                    .border(
                        1.2.dp,
                        if (draft.isSelected) PrimaryGold else TextGold.copy(alpha = 0.4f),
                        KhanaRadii.sm
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (draft.isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = DarkBrown1,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(ReviewSheetLayout.CHECKBOX_GAP))

            // Food Type Indicator - aligned with Type header
            Box(
                modifier = Modifier
                    .width(ReviewSheetLayout.FOOD_ICON_WIDTH)
                    .clickable { onToggleFoodType() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(KhanaBookTheme.iconSize.xsmall)
                        .border(1.dp, if (draft.foodType == "veg") VegGreen else NonVegRed, RoundedCornerShape(2.dp))
                        .padding(KhanaBookTheme.spacing.hairline),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (draft.foodType == "veg") VegGreen else NonVegRed, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.smallMedium))

            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = draft.name,
                    onValueChange = { onUpdateDraft(draft.copy(name = it)) },
                    textStyle = TextStyle(
                        color = if (draft.isSelected) TextLight else TextLight.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (!draft.isSelected) TextDecoration.LineThrough else null
                    ),
                    cursorBrush = SolidColor(PrimaryGold),
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                    }
                )

                BasicTextField(
                    value = draft.categoryName ?: "",
                    onValueChange = { onUpdateDraft(draft.copy(categoryName = it.ifBlank { null })) },
                    textStyle = TextStyle(
                        color = PrimaryGold.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    decorationBox = { innerTextField ->
                        if (draft.categoryName.isNullOrBlank()) {
                            Text("No Category", color = PrimaryGold.copy(alpha = 0.2f), fontSize = 11.sp)
                        }
                        innerTextField()
                    },
                    cursorBrush = SolidColor(PrimaryGold),
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                    }
                )
            }

            Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.extraSmall))

            if (draft.variants.size <= 1) {
                Row(
                    modifier = Modifier
                        .width(ReviewSheetLayout.PRICE_WIDTH)
                        .background(DarkBrown1.copy(alpha = 0.3f), KhanaRadii.sm)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("₹", color = PrimaryGold.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    BasicTextField(
                        value = if (draft.price == 0.0) "" else {
                            val i = draft.price.toLong()
                            if (draft.price == i.toDouble()) i.toString() else draft.price.toString()
                        },
                        onValueChange = { raw ->
                            val p = raw.toDoubleOrNull() ?: 0.0
                            onUpdateDraft(draft.copy(price = p))
                        },
                        textStyle = TextStyle(
                            color = if (draft.isSelected) TextLight else TextLight.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(PrimaryGold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).onFocusChanged {
                            if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                        }
                    )
                }
            }
        }


        if (draft.variants.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ReviewSheetLayout.CHECKBOX_WIDTH + ReviewSheetLayout.CHECKBOX_GAP - 4.dp, top = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                draft.variants.forEachIndexed { vIndex, variant ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (variant.isSelected) PrimaryGold.copy(alpha = 0.8f) else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (variant.isSelected) PrimaryGold else TextGold.copy(alpha = 0.4f),
                                    RoundedCornerShape(5.dp)
                                )
                                .clickable {
                                    val newVariants = draft.variants.toMutableList()
                                    newVariants[vIndex] = variant.copy(isSelected = !variant.isSelected)
                                    onUpdateDraft(draft.copy(variants = newVariants))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (variant.isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = DarkBrown1,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(KhanaBookTheme.spacing.smallMedium))

                        BasicTextField(
                            value = variant.name,
                            onValueChange = { newName ->
                                val newVariants = draft.variants.toMutableList()
                                newVariants[vIndex] = variant.copy(name = newName)
                                onUpdateDraft(draft.copy(variants = newVariants))
                            },
                            textStyle = TextStyle(
                                color = if (variant.isSelected) TextGold.copy(alpha = 0.8f) else TextGold.copy(alpha = 0.3f),
                                fontSize = 12.sp,
                                textDecoration = if (!variant.isSelected) TextDecoration.LineThrough else null
                            ),
                            cursorBrush = SolidColor(PrimaryGold),
                            modifier = Modifier.weight(1f).onFocusChanged {
                                if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                            }
                        )

                        Row(
                            modifier = Modifier
                                .width(72.dp)
                                .background(DarkBrown1.copy(alpha = 0.4f), KhanaRadii.md)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("₹", color = PrimaryGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = if (variant.price == 0.0) "" else {
                                    val i = variant.price.toLong()
                                    if (variant.price == i.toDouble()) i.toString() else variant.price.toString()
                                },
                                onValueChange = { p ->
                                    val newVariants = draft.variants.toMutableList()
                                    newVariants[vIndex] = variant.copy(price = p.toDoubleOrNull() ?: 0.0)
                                    onUpdateDraft(draft.copy(variants = newVariants))
                                },
                                textStyle = TextStyle(
                                    color = if (variant.isSelected) TextLight else TextLight.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.End
                                ),
                                cursorBrush = SolidColor(PrimaryGold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).onFocusChanged {
                                    if (it.isFocused) scope.launch { rowRequester.bringIntoView() }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
