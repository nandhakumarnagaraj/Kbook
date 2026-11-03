@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.reports.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.util.AppConstants
import com.khanabook.lite.pos.core.util.CurrencyUtils
import com.khanabook.lite.pos.core.util.DateUtils
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.OrderType
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.billing.data.getInvoiceNumberDisplay
import java.util.Calendar
import java.util.TimeZone

@Composable
fun OrderDetailsDialog(
    billWithItems: BillWithItems?,
    profile: RestaurantProfileEntity?,
    onDismiss: () -> Unit,
    enabledPaymentModes: List<PaymentMode> = emptyList(),
    onShareInvoice: ((BillWithItems) -> Unit)? = null,
    onPrintReceipt: ((BillWithItems) -> Unit)? = null,
    onResumeDraft: ((BillWithItems) -> Unit)? = null,
    onCancelOrder: ((BillWithItems) -> Unit)? = null,
    onChangePaymentMode: ((BillWithItems, PaymentMode) -> Unit)? = null
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val dialogWidthModifier = if (layout.isWideListDetail) {
        Modifier
            .fillMaxWidth(layout.dialogWidthFraction)
            .widthIn(max = layout.dialogMaxWidth)
    } else {
        Modifier.fillMaxWidth(0.94f)
    }

    var showPaymentModePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        KhanaBookCard(
            modifier = dialogWidthModifier
                .padding(spacing.medium),
            colors = CardDefaults.cardColors(containerColor = OrdersCard),
            shape = KhanaRadii.xl
        ) {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .fillMaxWidth()
            ) {
                // Title on the left, dismiss "X" on the right — gold, matching the
                // "Order Details" heading. The footer Close button was removed so this
                // is the single dismiss affordance.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Receipt,
                            contentDescription = null,
                            tint = OrdersGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text(
                            text = "Order Details",
                            color = OrdersGold,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close order details",
                            tint = OrdersGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                HorizontalDivider(color = OrdersDivider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(spacing.medium))

                if (billWithItems == null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ShimmerBox(height = 14.dp, modifier = Modifier.fillMaxWidth(0.5f))
                        Spacer(modifier = Modifier.height(spacing.small))
                        ShimmerBox(height = 14.dp, modifier = Modifier.fillMaxWidth(0.4f))
                        Spacer(modifier = Modifier.height(spacing.medium))
                        ShimmerBox(height = 12.dp, modifier = Modifier.fillMaxWidth(0.3f))
                        Spacer(modifier = Modifier.height(spacing.small))
                        repeat(3) {
                            SkeletonListItem()
                            Spacer(modifier = Modifier.height(spacing.extraSmall))
                        }
                        Spacer(modifier = Modifier.height(spacing.smallMedium))
                        ShimmerBox(height = 20.dp, modifier = Modifier.fillMaxWidth(0.5f))
                    }
                } else {
                    val bill = billWithItems.bill
                    val items = billWithItems.items
                    val statusValue = OrderStatus.fromDbValue(bill.orderStatus)
                    val isCancelled = statusValue == OrderStatus.CANCELLED
                    val isSameDay = isSameBusinessDay(bill.createdAt)

                    DetailRow(
                        "Order No:",
                        "#${bill.dailyOrderDisplay}",
                        valueColor = OrdersBrightGold,
                        fontWeight = FontWeight.Bold,
                        leadingIcon = Icons.Filled.Tag
                    )
                    Spacer(modifier = Modifier.height(spacing.small))
                    val invoiceLabel = if (profile?.gstEnabled == true) "Tax Invoice No:" else "Invoice No:"
                    DetailRow(invoiceLabel, bill.getInvoiceNumberDisplay(), leadingIcon = Icons.Filled.Receipt)
                    Spacer(modifier = Modifier.height(spacing.small))

                    val orderTypeDisplay = when (bill.orderType.trim().lowercase()) {
                        in OrderType.DINE_IN_ALIASES -> "Dine-in"
                        in OrderType.TAKEAWAY_ALIASES -> "Takeaway"
                        else -> bill.orderType.replaceFirstChar { it.uppercase() }
                    }
                    val orderTypeIcon = if (bill.orderType.trim().lowercase() in OrderType.TAKEAWAY_ALIASES) {
                        Icons.Filled.ShoppingBag
                    } else {
                        Icons.Filled.Restaurant
                    }
                    DetailRow("Order Type:", orderTypeDisplay, leadingIcon = orderTypeIcon)
                    Spacer(modifier = Modifier.height(spacing.small))
                    DetailRow("Date:", DateUtils.formatDisplay(bill.createdAt), leadingIcon = Icons.Filled.Schedule)

                    Spacer(modifier = Modifier.height(spacing.small))
                    Text("Items:", color = OrdersGold, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(spacing.small))

                    if (items.isEmpty()) {
                        Text(
                            text = "No items found in this order.",
                            color = OrdersTextSecondary.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = spacing.medium).align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp, max = layout.reportDetailItemListMaxHeight)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = layout.reportDetailItemListMaxHeight),
                                verticalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                items(items) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.itemName} x${item.quantity}",
                                            color = OrdersTextPrimary.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f).padding(end = spacing.small)
                                        )
                                        Text(
                                            text = CurrencyUtils.formatPrice(item.itemTotal),
                                            color = OrdersTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))
                    HorizontalDivider(color = OrdersDivider.copy(alpha = 0.7f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(spacing.medium))

                    DetailRow(
                        "Payment Mode:",
                        PaymentMode.fromDbValue(bill.paymentMode).displayLabel,
                        leadingIcon = getPayModeIcon(PaymentMode.fromDbValue(bill.paymentMode))
                    )
                    Spacer(modifier = Modifier.height(spacing.small))

                    // Total on its own gold-washed strip: the one number the cashier
                    // checks before handing the bill over.
                    Surface(
                        color = OrdersGold.copy(alpha = 0.14f),
                        shape = KhanaRadii.md,
                        border = BorderStroke(1.dp, OrdersDivider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Payments,
                                    contentDescription = null,
                                    tint = OrdersGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(spacing.small))
                                Text(
                                    text = "Total Amount",
                                    color = OrdersGold,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = CurrencyUtils.formatPrice(bill.totalAmount),
                                color = OrdersBrightGold,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }

                    if (isCancelled && bill.cancelReason.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(spacing.small))
                        DetailRow("Cancel Reason:", bill.cancelReason, valueColor = DangerRed, fontWeight = FontWeight.Bold)
                    }

                    // Print / Share are hidden once the bill is cancelled — a cancelled
                    // bill has no valid receipt to hand the customer.
                    if (!isCancelled) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        if (onPrintReceipt != null) {
                            Button(
                                onClick = { onPrintReceipt.invoke(billWithItems) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(spacing.buttonHeightLarge),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OrdersGold,
                                    contentColor = OrdersBg
                                ),
                                shape = KhanaRadii.lg
                            ) {
                                Icon(Icons.Filled.Receipt, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(spacing.small))
                                Text(
                                    "Print Receipt",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(spacing.small))
                        }
                        if (onShareInvoice != null) {
                            Button(
                                onClick = { onShareInvoice.invoke(billWithItems) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(spacing.buttonHeightLarge),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OrdersShareGreen,
                                    contentColor = Color.White
                                ),
                                shape = KhanaRadii.lg
                            ) {
                                Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(spacing.small))
                                Text(
                                    "Share Invoice",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(spacing.small))
                        }
                    }

                    // Payment-mode edits are a same-day, settled-bill correction only.
                    if (!isCancelled &&
                        isSameDay &&
                        statusValue == OrderStatus.COMPLETED &&
                        onChangePaymentMode != null &&
                        enabledPaymentModes.isNotEmpty()
                    ) {
                        OutlinedButton(
                            onClick = { showPaymentModePicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(spacing.buttonHeightLarge),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = OrdersGold
                            ),
                            border = BorderStroke(1.dp, OrdersGold.copy(alpha = 0.55f)),
                            shape = KhanaRadii.lg
                        ) {
                            Icon(Icons.Filled.Payments, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text(
                                "Change Payment Mode",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(spacing.small))
                    }

                    // Cancel is a same-day action on a live bill only.
                    if (!isCancelled && isSameDay && onCancelOrder != null) {
                        Button(
                            onClick = { onCancelOrder.invoke(billWithItems) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(spacing.buttonHeightLarge),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DangerRed,
                                contentColor = Color.White
                            ),
                            shape = KhanaRadii.lg
                        ) {
                            Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(spacing.small))
                            Text(
                                "Cancel Order",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.small))
                }
            }
        }
    }

    if (showPaymentModePicker && billWithItems != null && onChangePaymentMode != null) {
        val currentMode = PaymentMode.fromDbValue(billWithItems.bill.paymentMode)
        KhanaBookSelectionDialog(
            title = "Change Payment Mode",
            onDismissRequest = { showPaymentModePicker = false },
            options = enabledPaymentModes.map { mode ->
                SelectionDialogOption(
                    value = mode,
                    title = mode.displayLabel
                )
            },
            selectedValue = currentMode,
            cancelLabel = "Close",
            onOptionSelected = { mode ->
                showPaymentModePicker = false
                onChangePaymentMode.invoke(billWithItems, mode)
            }
        )
    }
}

/** True when [timestamp] falls on today's date in the restaurant's business timezone. */
private fun isSameBusinessDay(timestamp: Long): Boolean {
    val zone = TimeZone.getTimeZone(AppConstants.DEFAULT_TIMEZONE)
    val now = Calendar.getInstance(zone)
    val then = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun DetailStatusRow(label: String, statusText: String, kind: KhanaStatusKind) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextGold,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(end = spacing.small)
        )
        KhanaStatusBadge(text = statusText, kind = kind)
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = OrdersTextPrimary,
    fontWeight: FontWeight = FontWeight.Normal,
    leadingIcon: ImageVector? = null
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.padding(end = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = OrdersGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(spacing.extraSmall))
            }
            Text(
                text = label,
                color = OrdersGold,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = fontWeight),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

fun formatDate(date: String): String {
    if (date.contains(",")) return date.substringBefore(",").trim()
    return try {
        val datePart = date.split(" ").getOrNull(0) ?: return date
        val parts = datePart.split("-")
        if (parts.size != 3) return date
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } catch (e: Exception) {
        date
    }
}
