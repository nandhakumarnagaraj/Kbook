@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.menu.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.math.BigDecimal
import java.math.RoundingMode
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import com.khanabook.lite.pos.core.util.ValidationUtils
import com.khanabook.lite.pos.feature.menu.domain.MenuPricingRules
import com.khanabook.lite.pos.core.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.NonVegRed
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.core.theme.VegGreen

@Composable
fun ItemEditDialog(
    title: String,
    initialName: String = "",
    initialPrice: Double = 0.0,
    initialType: String = "veg",
    initialImageUrl: String? = null,
    initialImageVersion: Int = 0,
    variants: List<com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, List<EditableVariantDraft>) -> Unit = { _, _, _, _ -> },
    onConfirmWithPhoto: (
        name: String,
        price: Double,
        foodType: String,
        variants: List<EditableVariantDraft>,
        photoUri: Uri?,
        removePhoto: Boolean
    ) -> Unit = { name, price, type, vars, _, _ -> onConfirm(name, price, type, vars) }
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var price by remember(initialPrice) { mutableStateOf(formatPriceInput(initialPrice)) }
    var foodType by remember(initialType) { mutableStateOf(initialType) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var variantError by remember { mutableStateOf<String?>(null) }

    var showVariantEditor by remember { mutableStateOf(false) }
    var newVariantName by remember { mutableStateOf("") }
    var newVariantPrice by remember { mutableStateOf("") }
    var newVariantError by remember { mutableStateOf<String?>(null) }
    var editableVariants by remember(variants) {
        mutableStateOf(
            variants.map {
                EditableVariantDraft(
                    id = it.id,
                    name = it.variantName,
                    price = it.price.toDoubleOrNull() ?: 0.0,
                    priceText = formatPriceInput(it.price.toDoubleOrNull() ?: 0.0)
                )
            }
        )
    }

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isPhotoRemoved by remember { mutableStateOf(false) }
    var photoLoadFailed by remember(initialImageUrl, initialImageVersion) { mutableStateOf(false) }
    var photoLoading by remember(initialImageUrl, initialImageVersion) { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            isPhotoRemoved = false
            photoLoadFailed = false
        }
    }

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .imePadding()
            .heightIn(max = 720.dp),
        title = title,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Dish Photo Section ─────────────────────────────────────────â”€
                val storedModel = if (isPhotoRemoved) null else initialImageUrl
                val photoModel = selectedPhotoUri ?: storedModel
                val showPhotoPlaceholder = photoModel == null || photoLoadFailed
                val imageCacheKey = "$storedModel:$initialImageVersion"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(KhanaRadii.md)
                        .background(DarkBrown2)
                        .border(1.dp, BorderGold.copy(alpha = 0.35f), KhanaRadii.md)
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (!showPhotoPlaceholder) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photoModel)
                                .crossfade(true)
                                .apply {
                                    // Versioned keys only for the stored URL; a freshly
                                    // picked local Uri is unique and needs no cache key.
                                    if (photoModel == storedModel) {
                                        memoryCacheKey(imageCacheKey)
                                        diskCacheKey(imageCacheKey)
                                    }
                                }
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "Dish photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onLoading = { photoLoading = true },
                            onSuccess = {
                                photoLoading = false
                                photoLoadFailed = false
                            },
                            onError = {
                                photoLoading = false
                                photoLoadFailed = true
                            }
                        )
                        if (photoLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PrimaryGold,
                                strokeWidth = 2.dp
                            )
                        }
                        Surface(
                            color = DarkBrown1.copy(alpha = 0.78f),
                            shape = KhanaRadii.pill,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            TextButton(onClick = {
                                selectedPhotoUri = null
                                isPhotoRemoved = true
                                photoLoadFailed = false
                            }) {
                                Icon(Icons.Default.Delete, "Remove photo", tint = NonVegRed)
                                Text("Remove", color = TextLight)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = PrimaryGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = if (photoLoadFailed) "Photo couldn't load — tap to choose another"
                                       else "Tap to add a dish photo",
                                color = TextLight,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Optional · PNG, JPG or WebP · up to 10 MB",
                                color = TextGold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) nameError = null
                    },
                    label = { Text("Item Name", color = TextGold) },
                    placeholder = { Text("e.g. Paneer Butter Masala", color = TextGold.copy(alpha = 0.7f)) },
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
                            price = ValidationUtils.sanitizeDecimalInput(it, maxIntegerDigits = 6, maxFractionDigits = 2)
                            if (priceError != null) priceError = null
                        },
                        label = { Text("Base Price (₹)", color = TextGold) },
                        placeholder = { Text("e.g. 120", color = TextGold.copy(alpha = 0.7f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    TextButton(
                        onClick = {
                            showVariantEditor = !showVariantEditor
                            newVariantError = null
                        }
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Text(
                            if (showVariantEditor) "Close" else "Add Variant",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (showVariantEditor) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBrown2, KhanaRadii.md)
                            .border(1.dp, BorderGold.copy(alpha = 0.35f), KhanaRadii.md)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "New Variant",
                            color = TextLight,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = newVariantName,
                            onValueChange = {
                                newVariantName = it
                                newVariantError = null
                            },
                            label = { Text("Variant Name") },
                            placeholder = { Text("e.g. Small, Medium, Large") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = newVariantError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryGold,
                                unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight
                            )
                        )
                        OutlinedTextField(
                            value = newVariantPrice,
                            onValueChange = {
                                newVariantPrice = ValidationUtils.sanitizeDecimalInput(it, maxIntegerDigits = 6, maxFractionDigits = 2)
                                newVariantError = null
                            },
                            label = { Text("Price (₹)") },
                            placeholder = { Text("e.g. 80") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = newVariantError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryGold,
                                unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight
                            )
                        )
                        newVariantError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                        ) {
                            TextButton(onClick = {
                                showVariantEditor = false
                                newVariantName = ""
                                newVariantPrice = ""
                                newVariantError = null
                            }) {
                                Text("Cancel", color = TextGold)
                            }
                            TextButton(onClick = {
                                val parsedVariantPrice = newVariantPrice.toDoubleOrNull()
                                when {
                                    newVariantName.isBlank() -> newVariantError = "Variant name is required"
                                    newVariantPrice.isBlank() || parsedVariantPrice == null -> newVariantError = "Enter a valid variant price"
                                    parsedVariantPrice < 0.0 -> newVariantError = "Price cannot be negative"
                                    !MenuPricingRules.isValidPrice(parsedVariantPrice) -> newVariantError = MenuPricingRules.ERROR_MESSAGE
                                    else -> {
                                        editableVariants = editableVariants + EditableVariantDraft(
                                            name = newVariantName.trim(),
                                            price = parsedVariantPrice,
                                            priceText = newVariantPrice.trim()
                                        )
                                        price = ""
                                        newVariantName = ""
                                        newVariantPrice = ""
                                        showVariantEditor = false
                                    }
                                }
                            }) {
                                Text("Add", color = PrimaryGold)
                            }
                        }
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
                            value = variantDraft.priceText ?: formatPriceInput(variantDraft.price),
                            onValueChange = { raw ->
                                val sanitized = ValidationUtils.sanitizeDecimalInput(
                                    raw,
                                    maxIntegerDigits = 6,
                                    maxFractionDigits = 2
                                )
                                editableVariants = editableVariants.toMutableList().also { updated ->
                                    updated[index] = variantDraft.copy(
                                        priceText = sanitized,
                                        price = sanitized.toDoubleOrNull() ?: 0.0
                                    )
                                }
                                if (variantError != null) variantError = null
                            },
                            label = { Text("Price", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(0.3f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    else -> onConfirmWithPhoto(
                        normalizedName,
                        MenuPricingRules.resolveBasePrice(price, editableVariants.map { it.price })
                            .toDoubleOrNull() ?: 0.0,
                        foodType,
                        editableVariants.map { it.copy(name = it.name.trim()) },
                        selectedPhotoUri,
                        isPhotoRemoved
                    )
                }
            }
        ) {
            Text(if (title == "Add New Item") "Add Item" else "Save", color = PrimaryGold)
        }
    }

}

/**
 * Renders a stored price for editing without losing paise. toInt() would silently turn a
 * legal 120.99 into "120", and because the field is seeded from this value, a user who opens
 * an item and taps Save without touching the price would write 120.00 back.
 */
private fun formatPriceInput(value: Double): String {
    if (value == 0.0) return ""
    return BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
