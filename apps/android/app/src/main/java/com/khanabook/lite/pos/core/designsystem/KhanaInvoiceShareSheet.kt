package com.khanabook.lite.pos.core.designsystem

import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.util.DateUtils
import com.khanabook.lite.pos.core.util.buildInvoiceShareUrl
import com.khanabook.lite.pos.core.util.buildPendingInvoiceShareUrl
import com.khanabook.lite.pos.core.util.copyInvoiceLink
import com.khanabook.lite.pos.core.util.sendInvoiceViaSms
import com.khanabook.lite.pos.core.util.shareInvoiceViaWhatsAppLink
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.billing.data.getInvoiceNumberDisplay

/**
 * One share sheet for both Normal and Quick bills.
 *
 * WhatsApp and SMS are offered only when a customer number was captured; Copy Link and
 * the Android share sheet are always available, so a Quick bill (no number) can still
 * hand the customer the invoice without collecting a phone number first.
 */
@Composable
fun KhanaInvoiceShareSheet(
    billWithItems: BillWithItems,
    profile: RestaurantProfileEntity?,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bill = billWithItems.bill
    val hasPhone = !bill.customerWhatsapp.isNullOrBlank()

    val options = buildList {
        if (hasPhone) {
            add(SelectionDialogOption(value = "WhatsApp", title = "WhatsApp"))
            add(SelectionDialogOption(value = "SMS", title = "SMS"))
        }
        add(SelectionDialogOption(value = "Copy Link", title = "Copy Link"))
        add(SelectionDialogOption(value = "More apps…", title = "More apps…"))
    }

    KhanaBookSelectionDialog(
        title = "Share Invoice",
        onDismissRequest = onDismiss,
        options = options,
        selectedValue = null,
        message = null,
        trailingContent = {
            Text(
                text = "${bill.getInvoiceNumberDisplay()} • ${DateUtils.formatDateOnly(bill.createdAt)}",
                color = TextGold,
                style = MaterialTheme.typography.labelSmall
            )
        },
        cancelLabel = "Close",
        onOptionSelected = { label ->
            when (label) {
                "WhatsApp" -> shareInvoiceViaWhatsAppLink(context, billWithItems, profile)
                "SMS" -> sendInvoiceViaSms(context, billWithItems, profile)
                "Copy Link" -> {
                    val copied = copyInvoiceLink(context, billWithItems)
                    scope.launch {
                        if (copied != null) {
                            KhanaToast.show("Invoice link copied", ToastKind.Success)
                        } else {
                            KhanaToast.show(
                                "Invoice link not ready yet. Try again after sync.",
                                ToastKind.Warning
                            )
                        }
                    }
                }
                "More apps…" -> {
                    val link = buildInvoiceShareUrl(billWithItems)
                        ?: buildPendingInvoiceShareUrl(billWithItems)
                    if (link == null) {
                        scope.launch {
                            KhanaToast.show(
                                "Invoice link not ready yet. Try again after sync.",
                                ToastKind.Warning
                            )
                        }
                    } else {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "KhanaBook Invoice")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Invoice ${bill.getInvoiceNumberDisplay()}\n$link"
                            )
                        }
                        try {
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share Invoice via")
                            )
                        } catch (e: Exception) {
                            scope.launch {
                                KhanaToast.show("Unable to open share options.", ToastKind.Error)
                            }
                        }
                    }
                }
            }
            onOptionSelected(label)
        }
    )
}
