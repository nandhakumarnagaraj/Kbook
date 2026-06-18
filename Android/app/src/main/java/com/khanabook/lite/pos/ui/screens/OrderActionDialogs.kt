package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.ui.designsystem.PrimaryButton
import com.khanabook.lite.pos.ui.theme.KbShape
import com.khanabook.lite.pos.ui.theme.*

// ─────────────────────────────────────────────────────────────
//  Cancel Order Dialog
// ─────────────────────────────────────────────────────────────

private val CANCEL_REASONS = listOf(
    "Customer changed mind"  to "customer_request",
    "Item not available"     to "item_unavailable",
    "Duplicate order"        to "duplicate",
    "Customer didn't arrive" to "no_show"
)

@Composable
fun CancelOrderDialog(
    orderDisplay: String,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var otherText by remember { mutableStateOf("") }
    var showOther by remember { mutableStateOf(false) }

    val confirmEnabled = selectedReason != null && (!showOther || otherText.isNotBlank())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = KbError,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Cancel Order $orderDisplay?",
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "This action cannot be undone. Select a reason:",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                // Reason chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CANCEL_REASONS.forEach { (label, value) ->
                        val isSelected = selectedReason == value && !showOther
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedReason = value
                                showOther = false
                                otherText = ""
                            },
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KbError.copy(alpha = 0.12f),
                                selectedLabelColor = KbError
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = KbError,
                                borderColor = MaterialTheme.kbOutlineSubtle
                            )
                        )
                    }

                    // Other
                    val isOtherSelected = showOther
                    FilterChip(
                        selected = isOtherSelected,
                        onClick = {
                            showOther = true
                            selectedReason = "other"
                        },
                        label = { Text("Other", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KbError.copy(alpha = 0.12f),
                            selectedLabelColor = KbError
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isOtherSelected,
                            selectedBorderColor = KbError,
                            borderColor = MaterialTheme.kbOutlineSubtle
                        )
                    )

                    if (showOther) {
                        OutlinedTextField(
                            value = otherText,
                            onValueChange = { otherText = it },
                            placeholder = { Text("Describe the reason…") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KbBrandSaffron,
                                unfocusedBorderColor = MaterialTheme.kbOutlineSubtle
                            )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                        shape = KbShape.Medium
                    ) {
                        Text("Keep Order", color = MaterialTheme.kbTextPrimary)
                    }
                    Button(
                        onClick = {
                            val reason = if (showOther) otherText.trim()
                            else CANCEL_REASONS.firstOrNull { it.second == selectedReason }?.first ?: ""
                            onConfirm(reason)
                        },
                        enabled = confirmEnabled,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KbError,
                            disabledContainerColor = KbError.copy(alpha = 0.4f)
                        ),
                        shape = KbShape.Medium
                    ) {
                        Text("Cancel Order", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Refund Dialog (Easebuzz Online only)
// ─────────────────────────────────────────────────────────────

@Composable
fun RefundDialog(
    orderDisplay: String,
    paidAmount: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, reason: String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    var refundAmount by remember { mutableStateOf(paidAmount) }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var otherText by remember { mutableStateOf("") }
    var showOther by remember { mutableStateOf(false) }

    val amountValid = refundAmount.toDoubleOrNull()?.let { it > 0 } == true
    val confirmEnabled = amountValid && selectedReason != null && (!showOther || otherText.isNotBlank())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(spacing.medium)) {
                // Header
                Text(
                    "💸  Refund via Easebuzz",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Order $orderDisplay · Paid ₹$paidAmount via Easebuzz",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(16.dp))

                // Amount field
                Text(
                    "Refund Amount",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = refundAmount,
                    onValueChange = { refundAmount = it },
                    prefix = { Text("₹", color = KbBrandSaffron, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KbBrandSaffron,
                        unfocusedBorderColor = MaterialTheme.kbOutlineSubtle
                    ),
                    supportingText = {
                        if (!amountValid && refundAmount.isNotBlank())
                            Text("Enter a valid amount", color = KbError)
                        else
                            Text("Leave as full amount for complete refund", color = MaterialTheme.kbTextSecondary)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Partial refund hint
                val paidDouble = paidAmount.toDoubleOrNull() ?: 0.0
                val enteredDouble = refundAmount.toDoubleOrNull() ?: 0.0
                val isPartial = enteredDouble > 0 && enteredDouble < paidDouble - 0.01
                if (isPartial) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KbSuccess.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✓", fontSize = 16.sp, color = KbSuccess)
                        Text(
                            "Partial refund — order remains active",
                            color = KbSuccess,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    "Reason",
                    color = MaterialTheme.kbTextSecondary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(6.dp))

                // Reason chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CANCEL_REASONS.forEach { (label, value) ->
                        val isSelected = selectedReason == value && !showOther
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedReason = value
                                showOther = false
                                otherText = ""
                            },
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KbBrandSaffron.copy(alpha = 0.12f),
                                selectedLabelColor = KbBrandSaffron
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = KbBrandSaffron,
                                borderColor = MaterialTheme.kbOutlineSubtle
                            )
                        )
                    }
                    FilterChip(
                        selected = showOther,
                        onClick = { showOther = true; selectedReason = "other" },
                        label = { Text("Other") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KbBrandSaffron.copy(alpha = 0.12f),
                            selectedLabelColor = KbBrandSaffron
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = showOther,
                            selectedBorderColor = KbBrandSaffron,
                            borderColor = MaterialTheme.kbOutlineSubtle
                        )
                    )
                    if (showOther) {
                        OutlinedTextField(
                            value = otherText,
                            onValueChange = { otherText = it },
                            placeholder = { Text("Describe the reason…") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KbBrandSaffron,
                                unfocusedBorderColor = MaterialTheme.kbOutlineSubtle
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Info banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KbBrandSaffron.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ℹ️", fontSize = 16.sp)
                    Text(
                        "Refund reaches customer in 3–7 business days via the original payment method.",
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
                        shape = KbShape.Medium
                    ) {
                        Text("Cancel", color = MaterialTheme.kbTextPrimary)
                    }
                    PrimaryButton(
                        text = "Initiate Refund",
                        enabled = confirmEnabled,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val reason = if (showOther) otherText.trim()
                            else CANCEL_REASONS.firstOrNull { it.second == selectedReason }?.first ?: ""
                            onConfirm(refundAmount.trim(), reason)
                        }
                    )
                }
            }
        }
    }
}
