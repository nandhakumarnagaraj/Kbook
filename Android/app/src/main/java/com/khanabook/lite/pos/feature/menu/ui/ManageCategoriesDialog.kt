@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.menu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.khanabook.lite.pos.core.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.NonVegRed
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import kotlin.math.roundToInt

/**
 * Category manager (competitor-parity with KHIDE's "Manage Categories"):
 * drag to reorder, add in-line, rename/delete delegate to the shared dialogs,
 * and the new order is committed with an explicit "Save Category Order" action.
 */
@Composable
fun ManageCategoriesDialog(
    categories: List<CategoryEntity>,
    itemCounts: Map<Long, Int>,
    canWrite: Boolean,
    onAddCategory: (String, Int) -> Unit,
    onRenameCategory: (CategoryEntity) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onReorderCategories: (List<CategoryEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    // Reset local list whenever the backing data changes size (e.g. a category is added).
    val items: SnapshotStateList<CategoryEntity> =
        remember(categories.size) { mutableStateListOf<CategoryEntity>().apply { addAll(categories) } }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableIntStateOf(0) }

    var newCategoryName by remember { mutableStateOf("") }

    val orderChanged = items.map { it.id } != categories.map { it.id }

    fun moveDragged(step: Int) {
        val id = draggingId ?: return
        val from = items.indexOfFirst { it.id == id }
        val to = (from + step).coerceIn(0, items.lastIndex)
        if (to != from) {
            val moved = items.removeAt(from)
            items.add(to, moved)
        }
    }

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = "Manage Categories",
        dismissOnClickOutside = false,
        content = {
            Text(
                "Press and hold, then drag a category to reorder it.",
                color = TextGold.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                userScrollEnabled = draggingId == null
            ) {
                items(items, key = { it.id }) { category ->
                    val isDragging = draggingId == category.id
                    val index = items.indexOfFirst { it.id == category.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 2f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                scaleX = if (isDragging) 1.03f else 1f
                                scaleY = if (isDragging) 1.03f else 1f
                                shadowElevation = if (isDragging) 12f else 0f
                            }
                            .background(
                                color = if (isDragging) DarkBrown2.copy(alpha = 0.8f) else DarkBrown2,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .then(
                                if (isDragging) {
                                    Modifier.border(
                                        BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.8f))
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = spacing.smallMedium, vertical = spacing.extraSmall)
                            .onSizeChanged { if (rowHeightPx <= 0) rowHeightPx = it.height }
                            .pointerInput(category.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = category.id
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        val height = rowHeightPx.toFloat()
                                        if (height > 0f) {
                                            val step = (dragOffset / height).roundToInt()
                                            if (step != 0) {
                                                moveDragged(step)
                                                dragOffset -= step * height
                                            }
                                        }
                                    }
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = TextGold.copy(alpha = if (isDragging) 1f else 0.45f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.name,
                                color = TextLight,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isDragging) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val count = itemCounts[category.id] ?: 0
                            Text(
                                text = if (count == 0) "No items" else "$count item(s)",
                                color = TextGold.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (canWrite) {
                            IconButton(onClick = { onRenameCategory(category) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename ${category.name}",
                                    tint = TextGold
                                )
                            }
                            IconButton(onClick = { onDeleteCategory(category) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${category.name}",
                                    tint = NonVegRed
                                )
                            }
                        }
                    }
                }
            }

            if (canWrite) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("New Category", color = TextGold) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    IconButton(
                        onClick = {
                            val name = newCategoryName.trim()
                            if (name.isNotEmpty()) {
                                onAddCategory(
                                    name,
                                    (items.maxOfOrNull { it.sortOrder } ?: 0) + 1
                                )
                                newCategoryName = ""
                            }
                        },
                        enabled = newCategoryName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category", tint = PrimaryGold)
                    }
                }
            }
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextGold)
        }
        Button(
            onClick = { onReorderCategories(items.map { it }) },
            enabled = orderChanged && items.size > 1,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGold,
                contentColor = DarkBrown1
            )
        ) {
            Text(
                if (orderChanged) "Save Category Order" else "No Changes to Save",
                fontWeight = FontWeight.Bold
            )
        }
    }
}