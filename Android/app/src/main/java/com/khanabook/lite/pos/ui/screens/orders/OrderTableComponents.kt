@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.khanabook.lite.pos.ui.screens.orders

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.AppConstants
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.DateUtils
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*

// Column weights sized to content: Order No (short num) | Invoice (INV+num) | Mode (badge) | Status (badge) | Date
private val COL_ORDER   = 0.9f
private val COL_INVOICE = 1.7f
private val COL_MODE    = 1.7f
private val COL_STATUS  = 2.0f
private val COL_DATE    = 1.7f

@Composable
fun TableHeader(isGstEnabled: Boolean) {
    val spacing = KhanaBookTheme.spacing
    val invoiceHeader = if (isGstEnabled) "Tax Inv No" else "Invoice No"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBrown1.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .padding(vertical = spacing.small, horizontal = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("Order No", COL_ORDER)
        HeaderCell(invoiceHeader, COL_INVOICE)
        HeaderCell("Mode", COL_MODE)
        HeaderCell("Status", COL_STATUS)
        HeaderCell("Date", COL_DATE)
    }
}

@Composable
fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = TextGold,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        textAlign = TextAlign.Center,
        lineHeight = 12.sp
    )
}

@Composable
fun OrderTableRow(
    row: OrderDetailRow,
    enabledModes: List<PaymentMode>,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onShareText: () -> Unit,
    onRequestCancel: () -> Unit,
    onStatusChange: (String) -> Unit,
    onPayModeChange: (PaymentMode) -> Unit
) {
    var statusExpanded by remember { mutableStateOf(false) }
    var payModeExpanded by remember { mutableStateOf(false) }
    val spacing = KhanaBookTheme.spacing
    val isCancelled = row.orderStatus == OrderStatus.CANCELLED
    val rowShape = KhanaRadii.sm
    val rowBackground = when {
        isHighlighted -> PrimaryGold.copy(alpha = 0.18f)
        isCancelled -> DarkBrown1.copy(alpha = 0.15f)
        else -> DarkBrown1.copy(alpha = 0.35f)
    }
    val isTodayBill = remember(row.salesDate) {
        val billDate = java.time.Instant.ofEpochMilli(row.salesDate)
            .atZone(java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE))
            .toLocalDate()
        billDate == java.time.LocalDate.now(java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE))
    }
    val canEdit = !isCancelled && isTodayBill

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onShareText
            )
            .padding(vertical = spacing.hairline)
            .background(
                rowBackground,
                rowShape
            )
            .then(
                if (isHighlighted) {
                    Modifier.border(1.dp, PrimaryGold.copy(alpha = 0.9f), rowShape)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.extraSmall, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell(
                row.dailyNo, COL_ORDER,
                color = if (isCancelled) TextLight.copy(alpha = 0.35f) else TextLight
            )
            TableCell(
                row.invoiceDisplay, COL_INVOICE,
                fontWeight = FontWeight.Bold,
                color = if (isCancelled) TextLight.copy(alpha = 0.35f) else TextLight
            )

            Box(modifier = Modifier.weight(COL_MODE), contentAlignment = Alignment.Center) {
                val color = if (!canEdit) Color.Gray else getPayModeColor(row.payMode)
                Surface(
                    onClick = { if (canEdit) payModeExpanded = true },
                    color = color,
                    shape = KhanaRadii.sm,
                    modifier = Modifier.padding(horizontal = spacing.hairline)
                ) {
                    Text(
                        text = row.payMode.displayLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = payModeExpanded,
                    onDismissRequest = { payModeExpanded = false },
                    modifier = Modifier.background(DarkBrown2)
                ) {
                    enabledModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.displayLabel, color = TextLight, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onPayModeChange(mode); payModeExpanded = false }
                        )
                    }

                }
            }

            Box(modifier = Modifier.weight(COL_STATUS), contentAlignment = Alignment.Center) {
                val statusColor = when (row.orderStatus) {
                    OrderStatus.COMPLETED -> SuccessGreen
                    OrderStatus.CANCELLED -> DangerRed
                    else -> TextMuted
                }
                Surface(
                    onClick = { if (canEdit) statusExpanded = true },
                    color = statusColor,
                    shape = KhanaRadii.sm,
                    modifier = Modifier.padding(horizontal = spacing.extraSmall)
                ) {
                    Text(
                        text = when (row.orderStatus) {
                            OrderStatus.COMPLETED -> "Completed"
                            OrderStatus.CANCELLED -> "Cancelled"
                            else -> "Draft"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall),
                        lineHeight = 10.sp
                    )
                }
                if (canEdit) {
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                        modifier = Modifier.background(DarkBrown2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Completed", color = TextLight, style = MaterialTheme.typography.bodySmall) },
                            onClick = { onStatusChange(OrderStatus.COMPLETED.dbValue); statusExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)) },
                            onClick = { statusExpanded = false; onRequestCancel() }
                        )
                    }
                }
            }

            TableCell(DateUtils.formatDisplayDate(row.salesDate), COL_DATE, fontSize = 9.sp)
        }

        if (isCancelled && row.cancelReason.isNotBlank()) {
            Text(
                text = "Reason: ${row.cancelReason}",
                color = DangerRed.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = spacing.medium, bottom = spacing.extraSmall)
            )
        }
    }
}

@Composable
fun CancelOrderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val presetReasons = listOf("Wrong order", "Customer left", "Duplicate bill", "Test bill", "Other")
    var selectedReason by remember { mutableStateOf("Customer left") }
    var customReason by remember { mutableStateOf("") }

    KhanaBookSelectionDialog(
        title = "Cancel Order",
        onDismissRequest = onDismiss,
        message = "Select a reason:",
        options = presetReasons.map { reason ->
            SelectionDialogOption(
                value = reason,
                title = reason,
                selectedAccent = DangerRed,
                onSelect = {
                    selectedReason = reason
                    if (reason != "Other") customReason = ""
                }
            )
        },
        selectedValue = selectedReason,
        trailingContent = {
            if (selectedReason == "Other") {
                KhanaBookInputField(
                    value = customReason,
                    onValueChange = { customReason = it },
                    label = "Other Reason",
                    placeholder = "Describe the reason...",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
            }
        },
        cancelLabel = "Keep Order",
        actions = {
            TextButton(
                onClick = {
                    val finalReason = if (selectedReason == "Other") customReason.trim() else selectedReason
                    if (finalReason.isNotBlank()) onConfirm(finalReason)
                },
                enabled = selectedReason.isNotBlank() && (selectedReason != "Other" || customReason.isNotBlank())
            ) {
                Text("Cancel Order", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    ) {}
}

@Composable
fun PartAmountDialog(
    mode: PaymentMode,
    totalAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val labels = PaymentModeManager.getPartLabels(mode)
    val split = remember(totalAmount) { BillCalculator.splitPartPayment(totalAmount) }
    var p1Text by remember(totalAmount) { mutableStateOf(split.first) }
    var p2Text by remember(totalAmount) { mutableStateOf(split.second) }

    val p1 = p1Text.toDoubleOrNull() ?: 0.0
    val p2 = p2Text.toDoubleOrNull() ?: 0.0
    val isValid = BillCalculator.validatePartPayment(p1Text, p2Text, totalAmount)

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = mode.displayLabel,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text(
                    "Total: ${CurrencyUtils.formatPrice(totalAmount)}",
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    OutlinedTextField(
                        value = p1Text,
                        onValueChange = { p1Text = it },
                        label = { Text("${labels.first} Amount", color = TextGold.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        isError = !isValid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = p2Text,
                        onValueChange = { p2Text = it },
                        label = { Text("${labels.second} Amount", color = TextGold.copy(alpha = 0.6f)) },
                        modifier = Modifier.weight(1f),
                        isError = !isValid,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                }
                if (!isValid) {
                    Text(
                        "Sum must equal ${CurrencyUtils.formatPrice(totalAmount)} (Current: ${CurrencyUtils.formatPrice(p1 + p2)})",
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    ) {
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = TextGold, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = { onConfirm(p1Text, p2Text) },
            enabled = isValid
        ) {
            Text("Confirm", color = PrimaryGold, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = TextLight
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = color,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize, fontWeight = fontWeight),
        textAlign = TextAlign.Center,
        lineHeight = 12.sp
    )
}
