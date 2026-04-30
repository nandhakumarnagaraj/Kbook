package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.manager.InvoicePDFGenerator
import com.khanabook.lite.pos.domain.manager.TrustedExternalAppReturn
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*

/**
 * Utility functions for Date formatting and manipulation.
 */
object DateUtils {
    private const val DISPLAY_FORMAT = "dd MMM yyyy, hh:mm a"

    fun formatDisplay(timestamp: Long): String {
        return java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern(DISPLAY_FORMAT))
    }

    fun formatDateOnly(timestamp: Long): String {
        return java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
    }

    fun formatDisplayDate(timestamp: Long): String {
        return java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMMM\ndd, yyyy,\nHH:mm a"))
    }

    fun formatDisplayWithZone(timestamp: Long, zoneId: String): String {
        return java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.of(zoneId))
            .format(java.time.format.DateTimeFormatter.ofPattern(DISPLAY_FORMAT))
    }

    fun parseDb(timestamp: Long): Date {
        return Date(timestamp)
    }

    fun getStartOfDay(date: String, timezone: String = "Asia/Kolkata"): Long {
        return java.time.LocalDate.parse(date)
            .atStartOfDay(java.time.ZoneId.of(timezone))
            .toInstant()
            .toEpochMilli()
    }

    fun getEndOfDay(date: String, timezone: String = "Asia/Kolkata"): Long {
        return java.time.LocalDate.parse(date)
            .atTime(java.time.LocalTime.MAX)
            .atZone(java.time.ZoneId.of(timezone))
            .toInstant()
            .toEpochMilli()
    }
}

private const val UTILS_TAG = "KhanaBookUtils"

/**
 * Utility functions for Currency formatting.
 */
object CurrencyUtils {
    fun formatPrice(amount: Double, currency: String = "\u20b9"): String {
        return "$currency ${String.format("%.2f", amount)}"
    }

    fun formatPrice(amount: String?, currency: String = "\u20b9"): String {
        val d = amount?.toDoubleOrNull() ?: 0.0
        return formatPrice(d, currency)
    }

    /**
     * Compact form for dashboards: drops the .00 when the amount is a whole
     * rupee value, keeps two decimals when paise are present. Receipts and
     * invoices should keep using formatPrice() for full precision.
     */
    fun formatPriceCompact(amount: Double, currency: String = "\u20b9"): String {
        val whole = amount.toLong()
        return if (amount == whole.toDouble()) "$currency $whole"
        else "$currency ${String.format("%.2f", amount)}"
    }
}

/**
 * Extension function to find the nearest [ComponentActivity] from a [Context].
 */
fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Generates a plain-text version of the bill for SMS sharing.
 */
fun generateBillText(bill: BillWithItems, profile: RestaurantProfileEntity?): String {
    val sb = StringBuilder()
    val shopName = profile?.shopName ?: "BIRYANIWALE ANNA"
    val currency = if (profile?.currency == "INR" || profile?.currency == "Rupee") "Rs." else profile?.currency ?: ""
    
    sb.append("*${shopName.uppercase()}*\n")
    profile?.shopAddress?.takeIf { it.isNotBlank() }?.let { sb.append("$it\n") }
    profile?.gstin?.takeIf { it.isNotBlank() }?.let { sb.append("GSTIN: $it\n") }
    sb.append("--------------------------\n")
    sb.append("*Order ID:* #${bill.bill.dailyOrderDisplay.split("-").last()}\n")
    val invLabel = if (profile?.gstEnabled == true) "Tax Invoice No" else "Invoice No"
    sb.append("*$invLabel:* INV${bill.bill.lifetimeOrderId}\n")
    sb.append("*Date:* ${DateUtils.formatDisplay(bill.bill.createdAt)}\n")
    sb.append("--------------------------\n")
    
    for (item in bill.items) {
        val name = if (!item.variantName.isNullOrBlank()) 
            "${item.itemName} (${item.variantName})" 
        else item.itemName
        sb.append("${name.uppercase()} x ${item.quantity} = ${currency}${item.itemTotal}\n")
    }
    
    sb.append("--------------------------\n")
    sb.append("*Total Amount: ${currency}${bill.bill.totalAmount}*\n")
    sb.append("--------------------------\n")
    sb.append("${profile?.invoiceFooter?.takeIf { it.isNotBlank() } ?: "Thank you for your visit!"}\n")
    
    return sb.toString()
}

/**
 * Opens the native SMS app pre-filled with the invoice text and customer phone.
 * No permissions required — uses ACTION_SENDTO intent.
 */
fun sendInvoiceViaSms(context: Context, billWithItems: BillWithItems, profile: RestaurantProfileEntity?) {
    val text = generateBillText(billWithItems, profile)
    val raw = billWithItems.bill.customerWhatsapp
    val digits = raw?.replace(Regex("[^0-9]"), "") ?: ""
    // For Indian SMS: use 10-digit number; strip country code if present
    val smsPhone = when {
        digits.length == 10 -> digits
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        digits.length > 10 -> digits.takeLast(10)
        else -> ""
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = android.net.Uri.parse("smsto:$smsPhone")
        putExtra("sms_body", text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open SMS app.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Builds the public invoice URL hosted on the backend.
 * Returns null if the bill has not been synced yet (no serverId).
 */
fun buildInvoiceShareUrl(billWithItems: BillWithItems): String? {
    val serverId = billWithItems.bill.serverId ?: return null
    val publicToken = billWithItems.bill.publicToken ?: return null
    val restaurantId = billWithItems.bill.restaurantId
    val base = com.khanabook.lite.pos.BuildConfig.BACKEND_URL.trimEnd('/')
    return "$base/api/v1/public/invoice/$restaurantId/$serverId/$publicToken"
}

/**
 * Builds an invoice URL that can be shared immediately after local save.
 * The backend resolves it to the synced server invoice once sync catches up.
 */
fun buildPendingInvoiceShareUrl(billWithItems: BillWithItems): String? {
    val publicToken = billWithItems.bill.publicToken ?: return null
    val restaurantId = billWithItems.bill.restaurantId
    val deviceId = billWithItems.bill.deviceId.takeIf { it.isNotBlank() } ?: return null
    val localBillId = billWithItems.bill.id.takeIf { it > 0L } ?: return null
    val base = com.khanabook.lite.pos.BuildConfig.BACKEND_URL.trimEnd('/')
    return "$base/api/v1/public/invoice/pending/$restaurantId/${android.net.Uri.encode(deviceId)}/$localBillId/$publicToken"
}

/**
 * Shares the invoice on WhatsApp as a LINK only.
 * The link points to the backend's hosted invoice page.
 *
 * Requires the bill to be synced (serverId present). No Android-side invoice
 * text/PDF fallback is generated for WhatsApp.
 */
fun shareInvoiceViaWhatsAppLink(context: Context, billWithItems: BillWithItems, profile: RestaurantProfileEntity?) {
    val link = buildInvoiceShareUrl(billWithItems)
    if (link == null) {
        Toast.makeText(context, "Sync pending. Try again after the invoice syncs.", Toast.LENGTH_SHORT).show()
        return
    }
    shareInvoiceLink(context, billWithItems, profile, link)
}

fun shareInstantInvoiceLink(context: Context, billWithItems: BillWithItems, profile: RestaurantProfileEntity?) {
    val link = buildInvoiceShareUrl(billWithItems) ?: buildPendingInvoiceShareUrl(billWithItems)
    if (link == null) {
        Toast.makeText(context, "Invoice link not ready. Sharing invoice text.", Toast.LENGTH_SHORT).show()
        shareInvoiceTextViaWhatsApp(context, billWithItems, profile)
        return
    }
    shareInvoiceLink(context, billWithItems, profile, link)
}

private fun shareInvoiceLink(
    context: Context,
    billWithItems: BillWithItems,
    profile: RestaurantProfileEntity?,
    link: String
) {
    val shop = profile?.shopName?.takeIf { it.isNotBlank() } ?: "Invoice"
    val total = billWithItems.bill.totalAmount
    val currency = if (profile?.currency == "INR" || profile?.currency == "Rupee") "Rs." else profile?.currency ?: ""
    val message = "*$shop*\nInvoice INV${billWithItems.bill.lifetimeOrderId}\nTotal: $currency$total\n\nView: $link"

    val raw = billWithItems.bill.customerWhatsapp
    val digits = raw?.replace(Regex("[^0-9]"), "") ?: ""
    val formattedPhone = when {
        digits.length == 10 -> "91$digits"
        digits.length > 10 -> digits
        else -> null
    }
    val encoded = android.net.Uri.encode(message)

    if (formattedPhone != null) {
        try {
            val uri = android.net.Uri.parse("whatsapp://send?phone=$formattedPhone&text=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            TrustedExternalAppReturn.mark(context)
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            android.util.Log.w(UTILS_TAG, "WhatsApp direct URI failed, falling back to share sheet", e)
        }
    }

    val fallback = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(fallback, "Share Invoice via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open WhatsApp.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Shares a local invoice text immediately. Works before sync because it only
 * uses bill data already stored on the device.
 */
fun shareInvoiceTextViaWhatsApp(context: Context, billWithItems: BillWithItems, profile: RestaurantProfileEntity?) {
    val message = generateBillText(billWithItems, profile)
    val raw = billWithItems.bill.customerWhatsapp
    val digits = raw?.replace(Regex("[^0-9]"), "") ?: ""
    val formattedPhone = when {
        digits.length == 10 -> "91$digits"
        digits.length > 10 -> digits
        else -> null
    }
    val encoded = android.net.Uri.encode(message)

    if (formattedPhone != null) {
        try {
            val uri = android.net.Uri.parse("whatsapp://send?phone=$formattedPhone&text=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            TrustedExternalAppReturn.mark(context)
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            android.util.Log.w(UTILS_TAG, "WhatsApp text URI failed, falling back to share sheet", e)
        }
    }

    val fallback = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(fallback, "Share Invoice via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open share options.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens the PDF invoice in a viewer or print-capable app.
 */
fun openBillToPrint(context: Context, billWithItems: BillWithItems, profile: RestaurantProfileEntity?) {
    try {
        val pdfGenerator = InvoicePDFGenerator(context)
        val pdfFile = pdfGenerator.generatePDF(billWithItems, profile)
        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(pdfUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF to Print"))
    } catch (e: Exception) {
        Toast.makeText(
            context,
            UserMessageSanitizer.sanitize(e, "Unable to open invoice."),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Direct print functionality using the [BluetoothPrinterManager].
 */
fun directPrint(
    context: Context, 
    billWithItems: BillWithItems, 
    profile: RestaurantProfileEntity?, 
    printerManager: com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager
) {
    if (profile?.printerEnabled != true) {
        openBillToPrint(context, billWithItems, profile)
        return
    }

    val scope = (context.findActivity() as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope 
        ?: (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope
        ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    scope.launch(Dispatchers.IO) {
        if (!printerManager.isConnected() && !profile.printerMac.isNullOrBlank()) {
            printerManager.connect(profile.printerMac)
        }
        
        if (printerManager.isConnected()) {
            val bytes = InvoiceFormatter.formatForThermalPrinter(billWithItems, profile, context)
            printerManager.printBytes(bytes)
        } else {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.toast_printer_opening_pdf), Toast.LENGTH_SHORT).show()
                openBillToPrint(context, billWithItems, profile)
            }
        }
    }
}
