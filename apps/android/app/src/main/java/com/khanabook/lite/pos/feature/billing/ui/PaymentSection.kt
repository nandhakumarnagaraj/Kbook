@file:OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.billing.ui
import com.khanabook.lite.pos.core.theme.*

import android.graphics.BitmapFactory
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.khanabook.lite.pos.core.util.AppAssetStore
import androidx.compose.foundation.BorderStroke
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
import com.khanabook.lite.pos.core.components.ParchmentTextField
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.feature.billing.domain.BillCalculator
import com.khanabook.lite.pos.feature.billing.viewmodel.BillingViewModel.BillSummary

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

@Composable
fun CashTenderSection(
    payableAmount: Double,
    tenderedText: String,
    onTenderedChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val calculation = remember(payableAmount, tenderedText) {
        BillCalculator.calculateCashTender(payableAmount, tenderedText)
    }
    val suggestions = remember(payableAmount) {
        BillCalculator.getSuggestedCashDenominations(payableAmount)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkBrown2),
        shape = KhanaRadii.lg,
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cash Tendered & Change",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (tenderedText.isNotBlank()) {
                    Text(
                        "Clear",
                        color = TextGold.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clickable { onTenderedChange("") }
                            .padding(4.dp)
                    )
                }
            }

            // Quick suggestion chips
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    suggestions.forEach { note ->
                        val isExact = note == kotlin.math.ceil(payableAmount).toLong()
                        val isSelected = calculation.tenderedAmount.toLong() == note && tenderedText.isNotBlank()
                        Surface(
                            onClick = { onTenderedChange(note.toString()) },
                            modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                            shape = KhanaRadii.md,
                            color = if (isSelected) PrimaryGold else BrownSelected,
                            border = if (isSelected) null else BorderStroke(1.dp, BorderGold.copy(alpha = 0.6f)),
                            contentColor = if (isSelected) DarkBrown1 else TextLight
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isExact) "Exact\n₹$note" else "₹$note",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected || isExact) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }

            // Cash received input
            ParchmentTextField(
                value = tenderedText,
                onValueChange = onTenderedChange,
                label = "Cash Received from Customer (₹)",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Change Due / Shortage Banner
            if (calculation.isExactOrMore) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = KhanaRadii.md,
                    color = SuccessGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Change Due", color = SuccessGreen, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "₹" + CurrencyUtils.formatPrice(calculation.changeDue),
                                color = SuccessGreen,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else if (calculation.isPartial) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = KhanaRadii.md,
                    color = DangerRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Remaining Due", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "₹" + CurrencyUtils.formatPrice(calculation.shortage),
                                color = DangerRed,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

internal suspend fun loadShopLogoBlockingSection(
    context: android.content.Context,
    logoUrl: String?,
    logoPath: String?
): android.graphics.Bitmap? = com.khanabook.lite.pos.core.util.ShopLogoLoader.loadShopLogo(
    context = context,
    logoUrl = logoUrl,
    logoPath = logoPath,
    fallbackToDefault = true
)
