package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.data.remote.api.RawMaterialDto
import com.khanabook.lite.pos.ui.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.InventoryViewModel

@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = KhanaBookTheme.spacing
    var showAddDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var deletingId by remember { mutableStateOf<Long?>(null) }

    val editing = editingId?.let { id -> state.materials.find { it.id == id } }
    val deleting = deletingId?.let { id -> state.materials.find { it.id == id } }

    val configuredFoodCost by remember(state.foodCost) {
        derivedStateOf { state.foodCost.filter { it.configured == true } }
    }

    // Action failures surface as a toast — never wipe the list (load errors only).
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            KhanaToast.show(it, ToastKind.Error)
            viewModel.clearActionError()
        }
    }

    KhanaBookScreenScaffold(title = "Inventory", onBack = onBack) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGold)
            }
        } else if (state.loadError != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.loadError ?: "", color = ErrorPink, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(spacing.medium))
                    Button(onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) {
                        Text("Retry", color = DarkBrown1)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Raw Materials (${state.materials.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextLight, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Filled.Add, "Add material", tint = PrimaryGold)
                        }
                    }
                }

                if (state.materials.isEmpty()) {
                    item {
                        Text("No materials yet. Add flour, oil, paneer… and set recipes so stock deducts automatically on bills.",
                            style = MaterialTheme.typography.bodySmall, color = TextGold)
                    }
                }

                items(
                    count = state.materials.size,
                    key = { state.materials[it].id }
                ) { index ->
                    val material = state.materials[index]
                    MaterialCard(
                        material = material,
                        actionInFlight = state.actionInFlight,
                        onEditStock = { editingId = material.id },
                        onDelete = { deletingId = material.id }
                    )
                }

                // ── Insights ──
                item {
                    Spacer(Modifier.height(spacing.medium))
                    Text("Last 7 Days · Item Sales",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextLight, fontWeight = FontWeight.Bold)
                }
                if (state.itemSales.isEmpty()) {
                    item { Text("No sales in this period.", style = MaterialTheme.typography.bodySmall, color = TextGold) }
                }
                items(
                    count = state.itemSales.size,
                    key = { state.itemSales[it].menuItemId ?: it }
                ) { i ->
                    val row = state.itemSales[i]
                    InsightRow("${row.name}", "${row.quantitySold} sold Rs.${trimNum(row.revenue)}")
                }

                item {
                    Spacer(Modifier.height(spacing.small))
                    Text("Last 7 Days - Food Cost",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextLight, fontWeight = FontWeight.Bold)
                }
                if (configuredFoodCost.isEmpty()) {
                    item { Text("Add recipes to materials to see food-cost per item.",
                        style = MaterialTheme.typography.bodySmall, color = TextGold) }
                }
                items(
                    count = configuredFoodCost.size,
                    key = { configuredFoodCost[it].menuItemId ?: it }
                ) { i ->
                    val row = configuredFoodCost[i]
                    val margin = row.marginPct?.let { " - ${trimNum(it)}% margin" } ?: ""
                    InsightRow(row.name ?: "?",
                        "${row.quantitySold} sold Rs.${trimNum(row.revenue)} - cost Rs.${trimNum(row.cost)}$margin")
                }
            }
        }

        if (showAddDialog) {
            AddMaterialDialog(
                actionInFlight = state.actionInFlight,
                onDismiss = { showAddDialog = false },
                onCreate = { name, unit, stock, threshold, cost ->
                    viewModel.createMaterial(name, unit, stock, threshold, cost)
                    showAddDialog = false
                }
            )
        }

        editing?.let { material ->
            EditStockDialog(
                material = material,
                actionInFlight = state.actionInFlight,
                onDismiss = { editingId = null },
                onSave = { newStock ->
                    viewModel.updateStock(material.id, newStock)
                    editingId = null
                }
            )
        }

        deleting?.let { material ->
            AlertDialog(
                onDismissRequest = { deletingId = null },
                containerColor = DarkBrown2,
                shape = KhanaRadii.modal,
                title = {
                    Text("Delete ${material.name}?",
                        style = MaterialTheme.typography.titleLarge, color = TextLight)
                },
                text = {
                    Text("This removes the material and its recipe lines.",
                        style = MaterialTheme.typography.bodySmall, color = TextGold)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteMaterial(material.id)
                            deletingId = null
                        },
                        enabled = !state.actionInFlight,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("Delete", color = TextLight)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingId = null }) { Text("Cancel", color = PrimaryGold) }
                }
            )
        }
    }
}

private fun trimNum(v: Any?): String {
    val d = when (v) {
        is Number -> v.toDouble()
        else -> return "0"
    }
    return if (d % 1.0 == 0.0) d.toLong().toString() else String.format(java.util.Locale.US, "%.2f", d)
}

@Composable
private fun MaterialCard(
    material: RawMaterialDto,
    actionInFlight: Boolean,
    onEditStock: () -> Unit,
    onDelete: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Surface(shape = RoundedCornerShape(spacing.small), color = DarkBrown2,
        tonalElevation = spacing.extraSmall, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(material.name, style = MaterialTheme.typography.bodyLarge,
                    color = TextLight, fontWeight = FontWeight.SemiBold)
                val low = material.stockQuantity <= material.lowStockThreshold
                Text(
                    "${trimNum(material.stockQuantity)} ${material.unit}" +
                            (if (low) " · LOW (min ${trimNum(material.lowStockThreshold)})" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (low) WarningYellow else TextGold
                )
            }
            TextButton(onClick = onEditStock, enabled = !actionInFlight) {
                Text("Adjust", color = PrimaryGold)
            }
            IconButton(onClick = onDelete, enabled = !actionInFlight) {
                Icon(Icons.Filled.Delete, "Delete ${material.name}", tint = DangerRed)
            }
        }
    }
}

@Composable
private fun InsightRow(title: String, subtitle: String) {
    val spacing = KhanaBookTheme.spacing
    Surface(shape = RoundedCornerShape(spacing.small), color = DarkBrown2,
        tonalElevation = spacing.extraSmall, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextLight)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextGold)
        }
    }
}

@Composable
private fun AddMaterialDialog(
    actionInFlight: Boolean = false,
    onDismiss: () -> Unit,
    onCreate: (name: String, unit: String?, stock: Double?, threshold: Double?, cost: Double?) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var stock by remember { mutableStateOf("0") }
    var threshold by remember { mutableStateOf("0") }
    var cost by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        shape = KhanaRadii.modal,
        title = { Text("New Raw Material", style = MaterialTheme.typography.titleLarge, color = TextLight) },
        text = {
            Column {
                KhanaBookInputField(value = name, onValueChange = { name = it },
                    label = "Name (e.g. Flour)")
                Spacer(Modifier.height(spacing.small))
                KhanaBookInputField(value = unit, onValueChange = { unit = it }, label = "Unit (kg/L/pcs)")
                Spacer(Modifier.height(spacing.small))
                KhanaBookInputField(value = stock, onValueChange = { stock = it },
                    label = "Current stock", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(spacing.small))
                KhanaBookInputField(value = threshold, onValueChange = { threshold = it },
                    label = "Low-stock alert at", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(spacing.small))
                KhanaBookInputField(value = cost, onValueChange = { cost = it },
                    label = "Cost per unit (optional)", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = {
                onCreate(name, unit.ifBlank { "kg" }, stock.toDoubleOrNull(),
                    threshold.toDoubleOrNull(), cost.toDoubleOrNull())
            }, enabled = name.isNotBlank() && !actionInFlight,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) {
                Text("Save", color = DarkBrown1)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PrimaryGold) } }
    )
}

@Composable
private fun EditStockDialog(
    material: RawMaterialDto,
    actionInFlight: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var stock by remember { mutableStateOf(trimNum(material.stockQuantity)) }
    val parsedStock = stock.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        shape = KhanaRadii.modal,
        title = { Text("Adjust ${material.name}", style = MaterialTheme.typography.titleLarge, color = TextLight) },
        text = {
            Column {
                Text("Current: ${trimNum(material.stockQuantity)} ${material.unit}",
                    style = MaterialTheme.typography.bodySmall, color = TextGold)
                Spacer(Modifier.height(spacing.small))
                KhanaBookInputField(value = stock, onValueChange = { stock = it },
                    label = "New stock (${material.unit})",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = { parsedStock?.let(onSave) },
                enabled = parsedStock != null && !actionInFlight,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold)) {
                Text("Save", color = DarkBrown1)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PrimaryGold) } }
    )
}
