@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.menuconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.domain.util.MenuPricingRules
import com.khanabook.lite.pos.ui.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.NonVegRed
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.VegGreen

@Composable
fun ItemEditDialog(
    title: String,
    initialName: String = "",
    initialPrice: Double = 0.0,
    initialType: String = "veg",
    variants: List<com.khanabook.lite.pos.data.local.entity.ItemVariantEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, List<Pair<String, Double>>) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var price by remember(initialPrice) { mutableStateOf(if (initialPrice == 0.0) "" else initialPrice.toInt().toString()) }
    var foodType by remember(initialType) { mutableStateOf(initialType) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var variantError by remember { mutableStateOf<String?>(null) }

    var showAddVariantDialog by remember { mutableStateOf(false) }
    var editableVariants by remember(variants) {
        mutableStateOf(
            variants.map {
                EditableVariantDraft(
                    name = it.variantName,
                    price = it.price.toDoubleOrNull() ?: 0.0
                )
            }
        )
    }

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) nameError = null
                    },
                    label = { Text("Item Name", color = TextGold) },
                    isError = nameError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGold,
                        unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        nameError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                if (editableVariants.isEmpty()) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = {
                            price = it
                            if (priceError != null) priceError = null
                        },
                        label = { Text("Base Price (₹)", color = TextGold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = priceError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            priceError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = foodType == "veg",
                            onClick = { foodType = "veg" },
                            colors = RadioButtonDefaults.colors(selectedColor = VegGreen)
                        )
                        Text("Veg", color = TextLight)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = foodType == "non-veg",
                            onClick = { foodType = "non-veg" },
                            colors = RadioButtonDefaults.colors(selectedColor = NonVegRed)
                        )
                        Text("Non-Veg", color = TextLight)
                    }
                }

                HorizontalDivider(color = BorderGold.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Variants", color = PrimaryGold, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddVariantDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Text("Add Variant", style = MaterialTheme.typography.labelMedium)
                    }
                }

                editableVariants.forEachIndexed { index, variantDraft ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = variantDraft.name,
                            onValueChange = {
                                editableVariants = editableVariants.toMutableList().also { updated ->
                                    updated[index] = variantDraft.copy(name = it)
                                }
                                if (variantError != null) variantError = null
                            },
                            label = { Text("Name", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(0.7f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextLight, unfocusedTextColor = TextLight)
                        )
                        OutlinedTextField(
                            value = if (variantDraft.price == 0.0) "" else variantDraft.price.toInt().toString(),
                            onValueChange = {
                                val parsed = it.toDoubleOrNull()
                                if (it.isBlank() || parsed == null || parsed >= 0.0) {
                                    editableVariants = editableVariants.toMutableList().also { updated ->
                                        updated[index] = variantDraft.copy(price = parsed ?: 0.0)
                                    }
                                    if (variantError != null) variantError = null
                                }
                            },
                            label = { Text("Price", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(0.3f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextLight, unfocusedTextColor = TextLight)
                        )
                        IconButton(onClick = {
                            editableVariants = editableVariants.toMutableList().also { updated ->
                                updated.removeAt(index)
                            }
                        }) {
                            Icon(Icons.Default.Delete, null, tint = NonVegRed.copy(alpha = 0.7f), modifier = Modifier.size(KhanaBookTheme.iconSize.small))
                        }
                    }
                }

                variantError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextGold)
        }
        TextButton(
            onClick = {
                val normalizedName = name.trim()
                val parsedPrice = price.toDoubleOrNull()
                nameError = null
                priceError = null
                variantError = null

                val hasInlineVariants = editableVariants.isNotEmpty()
                val invalidDraftVariant = editableVariants.firstOrNull { it.name.isBlank() || it.price < 0.0 }
                val outOfRangeDraftVariant = editableVariants.firstOrNull {
                    !MenuPricingRules.isValidPrice(it.price)
                }

                when {
                    normalizedName.isBlank() -> nameError = "Item name is required"
                    !hasInlineVariants && price.isBlank() -> priceError = "Enter a valid item price"
                    !hasInlineVariants && parsedPrice == null -> priceError = "Enter a valid item price"
                    !hasInlineVariants && (parsedPrice ?: 0.0) < 0.0 -> priceError = "Price cannot be negative"
                    !hasInlineVariants && !MenuPricingRules.isValidPrice(parsedPrice) -> priceError = MenuPricingRules.ERROR_MESSAGE
                    invalidDraftVariant != null -> variantError = "Enter a valid item price"
                    outOfRangeDraftVariant != null -> variantError = MenuPricingRules.ERROR_MESSAGE
                    else -> onConfirm(
                        normalizedName,
                        parsedPrice ?: 0.0,
                        foodType,
                        editableVariants.map { it.name.trim() to it.price }
                    )
                }
            }
        ) {
            Text("Save", color = PrimaryGold)
        }
    }

    if (showAddVariantDialog) {
        var newVName by remember { mutableStateOf("") }
        var newVPrice by remember { mutableStateOf("") }
        var newVariantError by remember { mutableStateOf<String?>(null) }

        KhanaBookDialog(
            onDismissRequest = { showAddVariantDialog = false },
            title = "Add Variant",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newVName, onValueChange = { newVName = it }, label = { Text("Variant Name") })
                    OutlinedTextField(value = newVPrice, onValueChange = { newVPrice = it; newVariantError = null }, label = { Text("Price (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = newVariantError != null)
                    newVariantError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
        ) {
            TextButton(onClick = { showAddVariantDialog = false }) {
                Text("Cancel", color = TextGold)
            }
            TextButton(onClick = {
                val parsedVariantPrice = newVPrice.toDoubleOrNull()
                when {
                    newVName.isBlank() -> newVariantError = "Item name is required"
                    newVPrice.isBlank() || parsedVariantPrice == null -> newVariantError = "Enter a valid item price"
                    (parsedVariantPrice ?: 0.0) < 0.0 -> newVariantError = "Price cannot be negative"
                    !MenuPricingRules.isValidPrice(parsedVariantPrice) -> newVariantError = MenuPricingRules.ERROR_MESSAGE
                    else -> {
                        val variantPrice = parsedVariantPrice ?: 0.0
                        editableVariants = editableVariants + EditableVariantDraft(
                            name = newVName.trim(),
                            price = variantPrice
                        )
                        price = ""
                        showAddVariantDialog = false
                    }
                }
            }) {
                Text("Add", color = PrimaryGold)
            }
        }
    }
}
