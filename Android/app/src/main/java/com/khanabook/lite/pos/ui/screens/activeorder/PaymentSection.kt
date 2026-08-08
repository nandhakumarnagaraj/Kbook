@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens.activeorder

import android.graphics.BitmapFactory
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.khanabook.lite.pos.domain.util.AppAssetStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.BillingViewModel.BillSummary

@Composable
fun BillSummaryDisplay(summary: BillSummary) {
    val spacing = KhanaBookTheme.spacing
    val subtotal = summary.subtotal.toDoubleOrNull() ?: 0.0
    val cgst = summary.cgst.toDoubleOrNull() ?: 0.0
    val sgst = summary.sgst.toDoubleOrNull() ?: 0.0
    val customTax = summary.customTax.toDoubleOrNull() ?: 0.0
    val total = summary.total.toDoubleOrNull() ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
        shape = KhanaRadii.lg
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) {
            SummaryRow("Subtotal", CurrencyUtils.formatPrice(subtotal), TextGold)
            if (cgst > 0) SummaryRow("CGST", CurrencyUtils.formatPrice(cgst), TextGold.copy(alpha = 0.7f))
            if (sgst > 0) SummaryRow("SGST", CurrencyUtils.formatPrice(sgst), TextGold.copy(alpha = 0.7f))
            if (customTax > 0) SummaryRow("Custom Tax", CurrencyUtils.formatPrice(customTax), TextGold.copy(alpha = 0.7f))
            HorizontalDivider(color = BorderGold.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = spacing.small))
            SummaryRow("Total", CurrencyUtils.formatPrice(total), PrimaryGold, bold = true)
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = color, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            color = color,
            style = if (bold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun PaymentModeSelector(
    enabledModes: List<PaymentMode>,
    selectedMode: PaymentMode,
    onModeChange: (PaymentMode) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(KhanaBookTheme.spacing.buttonHeightCompact)
            .background(BrownSelected, KhanaRadii.md)
            .border(1.dp, BorderGold)
            .clickable { onExpandedChange(true) }
            .padding(horizontal = spacing.medium),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selectedMode.displayLabel, color = PrimaryGold, style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Default.ArrowDropDown, null, tint = PrimaryGold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(DarkBrown2)
        ) {
            enabledModes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayLabel, color = TextLight) },
                    onClick = {
                        onModeChange(mode)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

internal suspend fun loadShopLogoBlocking(
    context: android.content.Context,
    logoUrl: String?,
    logoPath: String?
): android.graphics.Bitmap? {
    if (!logoUrl.isNullOrBlank()) {
        try {
            val request = ImageRequest.Builder(context)
                .data(logoUrl)
                .allowHardware(false)
                .size(128)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
            if (bitmap != null) return bitmap
        } catch (_: Exception) { }
    }
    return AppAssetStore.resolveAssetPath(logoPath)?.let { path ->
        try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
    }
}
