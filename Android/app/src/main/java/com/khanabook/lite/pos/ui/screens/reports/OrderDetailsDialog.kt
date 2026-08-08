@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.getInvoiceNumberDisplay
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.CurrencyUtils
import com.khanabook.lite.pos.domain.util.DateUtils
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*

@Composable
fun OrderDetailsDialog(
    billWithItems: BillWithItems?,
    profile: RestaurantProfileEntity?,
    onDismiss: () -> Unit,
    onShareInvoice: ((BillWithItems) -> Unit)? = null,
    onPrintReceipt: ((BillWithItems) -> Unit)? = null,
    onResumeDraft: ((BillWithItems) -> Unit)? = null,
    onCancelOrder: ((BillWithItems) -> Unit)? = null
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        KhanaBookCard(
            modifier = dialogWidthModifier
                .padding(spacing.medium),
            colors = CardDefaults.cardColors(containerColor = DarkBrown1),
            shape = KhanaRadii.xl
        ) {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Order Details",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = PrimaryGold)
                    }
                }

                HorizontalDivider(color = BorderGold.copy(alpha = 0.5f), thickness = 1.dp)
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

                    DetailRow("Order No:", "#${bill.dailyOrderDisplay.split("-").last()}", valueColor = PrimaryGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(spacing.small))
                    val invoiceLabel = if (profile?.gstEnabled == true) "Tax Invoice No:" else "Invoice No:"
                    DetailRow(invoiceLabel, bill.getInvoiceNumberDisplay())
                    Spacer(modifier = Modifier.height(spacing.small))
                    DetailRow("Date:", DateUtils.formatDisplay(bill.createdAt))
                    Spacer(modifier = Modifier.height(spacing.small))
                    val orderTypeDisplay = when (bill.orderType.trim().lowercase()) {
                        "dine_in", "dine-in" -> "Dine-in"
                        "takeaway", "take_away" -> "Takeaway"
                        else -> bill.orderType.replaceFirstChar { it.uppercase() }
                    }
                    DetailRow("Order Type:", orderTypeDisplay)

                    Spacer(modifier = Modifier.height(spacing.medium))
                    Text("Items:", color = TextGold, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(spacing.small))

                    if (items.isEmpty()) {
                        Text(
                            text = "No items found in this order.",
                            color = TextLight.copy(alpha = 0.5f),
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
                                            color = TextLight.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f).padding(end = spacing.small)
                                        )
                                        Text(
                                            text = CurrencyUtils.formatPrice(item.itemTotal),
                                            color = TextLight,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.medium))
                    HorizontalDivider(color = BorderGold.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(spacing.medium))
                    
                    DetailRow("Payment Mode:", PaymentMode.fromDbValue(bill.paymentMode).displayLabel)
                    Spacer(modifier = Modifier.height(spacing.small))
                    DetailRow("Total Amount:", CurrencyUtils.formatPrice(bill.totalAmount), valueColor = PrimaryGold, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(spacing.small))

                    val statusValue = OrderStatus.fromDbValue(bill.orderStatus)
                    val statusText = when (statusValue) {
                        OrderStatus.DRAFT -> "Pending"
                        else -> statusValue.name.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    val statusColor = when (statusValue) {
                        OrderStatus.COMPLETED -> VegGreen
                        OrderStatus.CANCELLED -> ZomatoRed
                        else -> TextGold
                    }

                    DetailRow("Status:", statusText, valueColor = statusColor, fontWeight = FontWeight.Bold)
                    if (statusValue == OrderStatus.CANCELLED && bill.cancelReason.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(spacing.small))
                        DetailRow("Cancel Reason:", bill.cancelReason, valueColor = ZomatoRed, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(spacing.medium))
                }

                Spacer(modifier = Modifier.height(spacing.large))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightLarge),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = KhanaRadii.lg
                ) {
                    Text("Close", color = DarkBrown1, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
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
fun DetailRow(label: String, value: String, valueColor: Color = TextLight, fontWeight: FontWeight = FontWeight.Normal) {
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
