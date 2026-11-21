@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.reports.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt

import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.core.touch.TouchAwareDialog
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
    onChangePaymentMode: ((BillWithItems, PaymentMode) -> Unit)? = null,
    /** Vertical position (0..1) of the order row that opened this dialog. */
    originY: Float = 0.5f
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

    TouchAwareDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        KhanaBookCard(
            modifier = dialogWidthModifier
                .padding(spacing.medium)
                .dialogEntrance(TransformOrigin(0.5f, originY.coerceIn(0f, 1f))),
            colors = CardDefaults.cardColors(containerColor = OrdersCard),
            shape = KhanaRadii.xl
        ) {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .fillMaxWidth()
            ) {
                // Title centered across the header, gold dismiss "X" pinned right.
                // The order number lives in the details card instead. The footer Close
                // button was removed so this is the single dismiss affordance.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Order Details",
                        color = OrdersGold,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 44.dp)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close order details",
                            tint = OrdersGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.extraSmall))
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

                    // Order identity is one quiet card: muted labels, white values, no
                    // competing icons.
                    Surface(
                        color = OrdersBg.copy(alpha = 0.25f),
                        shape = KhanaRadii.md,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = spacing.medium,
                                vertical = spacing.smallMedium
                            ),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            DetailRow("Order No", bill.dailyOrderDisplay)
                            val invoiceLabel =
                                if (profile?.gstEnabled == true) "Tax Invoice No" else "Invoice No"
                            DetailRow(invoiceLabel, bill.getInvoiceNumberDisplay())

                            val orderTypeDisplay = when (bill.orderType.trim().lowercase()) {
                                in OrderType.DINE_IN_ALIASES -> "Dine-in"
                                in OrderType.TAKEAWAY_ALIASES -> "Takeaway"
                                else -> bill.orderType.replaceFirstChar { it.uppercase() }
                            }
                            DetailRow("Order Type", orderTypeDisplay)
                            DetailRow("Date", DateUtils.formatDisplay(bill.createdAt))
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.smallMedium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Items (${items.size})",
                            color = OrdersTextPrimary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (items.isNotEmpty()) {
                            Text(
                                text = "Amount",
                                color = OrdersTextPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
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
                                    .heightIn(max = layout.reportDetailItemListMaxHeight)
                            ) {
                                itemsIndexed(items) { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = spacing.small),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.itemName,
                                            color = OrdersTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = spacing.small)
                                        )
                                        Text(
                                            text = "x${item.quantity}",
                                            color = OrdersTextPrimary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(modifier = Modifier.width(spacing.smallMedium))
                                        Text(
                                            text = CurrencyUtils.formatPrice(item.itemTotal),
                                            color = OrdersTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                    if (index != items.lastIndex) {
                                        HorizontalDivider(
                                            color = OrdersDivider.copy(alpha = 0.45f),
                                            thickness = 0.5.dp
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
                        "Payment Mode",
                        PaymentMode.fromDbValue(bill.paymentMode).displayLabel
                    )
                    Spacer(modifier = Modifier.height(spacing.small))

                    // Total is the one number the cashier checks before handing the bill
                    // over: plain row, white amount, no competing gold wash.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.small),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Amount",
                            color = OrdersTextPrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = CurrencyUtils.formatPrice(bill.totalAmount),
                            color = OrdersTextPrimary,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    if (isCancelled && bill.cancelReason.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(spacing.small))
                        DetailRow("Cancel Reason:", bill.cancelReason, valueColor = DangerRed, fontWeight = FontWeight.Bold)
                    }

                    // Print / Share are hidden once the bill is cancelled — a cancelled
                    // bill has no valid receipt to hand the customer.
                    val canPrintOrShare = !isCancelled &&
                        (onPrintReceipt != null || onShareInvoice != null)
                    // Payment-mode edits are a same-day, settled-bill correction only.
                    val canChangePayment = !isCancelled &&
                        isSameDay &&
                        statusValue == OrderStatus.COMPLETED &&
                        onChangePaymentMode != null &&
                        enabledPaymentModes.isNotEmpty()
                    // Cancel is a same-day action on a live bill only.
                    val canCancel = !isCancelled && isSameDay && onCancelOrder != null

                    if (canPrintOrShare || canChangePayment || canCancel) {
                        Spacer(modifier = Modifier.height(spacing.medium))
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            if (canPrintOrShare) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
                                ) {
                                    if (onPrintReceipt != null) {
                                        Button(
                                            onClick = { onPrintReceipt.invoke(billWithItems) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(spacing.buttonHeightLarge),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = OrdersGold,
                                                contentColor = OrdersBg
                                            ),
                                            shape = KhanaRadii.lg
                                        ) {
                                            Icon(
                                                Icons.Filled.Receipt,
                                                null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(spacing.small))
                                            Text(
                                                "Print",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                    if (onShareInvoice != null) {
                                        Button(
                                            onClick = { onShareInvoice.invoke(billWithItems) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(spacing.buttonHeightLarge),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = OrdersShareGreen,
                                                contentColor = Color.White
                                            ),
                                            shape = KhanaRadii.lg
                                        ) {
                                            Icon(
                                                Icons.Filled.Share,
                                                null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(spacing.small))
                                            Text(
                                                "Share",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            if (canChangePayment) {
                                Button(
                                    onClick = { showPaymentModePicker = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(spacing.buttonHeightLarge),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OrdersBronze,
                                        contentColor = Color.White
                                    ),
                                    shape = KhanaRadii.lg
                                ) {
                                    Icon(
                                        Icons.Filled.Payments,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text(
                                        "Change Payment Mode",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                            if (canCancel) {
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
                                    Icon(
                                        Icons.Filled.Cancel,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(spacing.small))
                                    Text(
                                        "Cancel Order",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
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
                    title = mode.displayLabel,
                    leadingIcon = getPayModeIcon(mode)
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
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = OrdersTextPrimary,
    fontWeight: FontWeight = FontWeight.SemiBold,
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
                    tint = OrdersTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(spacing.extraSmall))
            }
            Text(
                text = label,
                color = OrdersTextPrimary,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
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
