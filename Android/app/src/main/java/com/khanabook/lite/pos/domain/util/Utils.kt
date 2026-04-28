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
 * Generates a plain-text version of the bill for WhatsApp sharing.
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
    sb.append("Thank you for your visit!\n")
    
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
    val restaurantId = billWithItems.bill.restaurantId
    val base = com.khanabook.lite.pos.BuildConfig.BACKEND_URL.trimEnd('/')
    return "$base/api/v1/public/invoice/$restaurantId/$serverId"
}

/**
 * Shares the invoice on WhatsApp as a LINK only.
 * The link points to the backend's hosted invoice page.
 *
 * Requires the bill to be synced (serverId present). If not synced,
 * falls back to sending the full invoice text — same behaviour as before
 * so the user is never blocked.
 *
 * Uses whatsapp://send (direct app URI) so it works offline — WhatsApp
 * queues the message and delivers when internet returns.
 */
fun shareInvoiceViaWhatsAppLink(context: Context, billWithItems: BillWithItems, profile: RestaurantProfileEntity?) {
    val link = buildInvoiceShareUrl(billWithItems)
    val message = if (link != null) {
        val shop = profile?.shopName?.takeIf { it.isNotBlank() } ?: "Invoice"
        val total = billWithItems.bill.totalAmount
        val currency = if (profile?.currency == "INR" || profile?.currency == "Rupee") "Rs." else profile?.currency ?: ""
        "*$shop*\nInvoice INV${billWithItems.bill.lifetimeOrderId}\nTotal: $currency$total\n\nView: $link"
    } else {
        Toast.makeText(context, "Sync pending — sharing as text", Toast.LENGTH_SHORT).show()
        generateBillText(billWithItems, profile)
    }

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
 * Shares the bill as a text message on WhatsApp.
 * This method works WITHOUT contact permission and does NOT save temporary contacts.
 */
fun shareBillTextOnWhatsApp(context: Context, billWithItems: BillWithItems, profile: RestaurantProfileEntity?) {
    val text = generateBillText(billWithItems, profile)
    val rawPhone = billWithItems.bill.customerWhatsapp
    val digits = rawPhone?.replace(Regex("[^0-9]"), "") ?: ""
    val formattedPhone = when {
        digits.length == 10 -> "91$digits"
        digits.length > 10 -> digits
        else -> null
    }

    if (formattedPhone != null) {
        try {
            val uri = android.net.Uri.parse("whatsapp://send?phone=$formattedPhone&text=${android.net.Uri.encode(text)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            TrustedExternalAppReturn.mark(context)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to generic share if WhatsApp not installed or URI fails
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Invoice via"))
        }
    } else {
        // No number, just generic share
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Invoice via"))
    }
}

/**
 * Shares a bill/invoice via WhatsApp.
 * Shares the PDF through the best available WhatsApp path without
 * READ_CONTACTS/WRITE_CONTACTS permission.
 *
 * Strategy:
 * 1. Copy the number so it is ready if WhatsApp opens search.
 * 2. Try direct PDF targeting via WhatsApp jid.
 * 3. If that cannot launch, open WhatsApp's PDF picker so
 *    the user can paste the number into WhatsApp search and select the unsaved chat.
 * 4. Last resort: generic PDF share sheet.
 *
 * Text-only WhatsApp sharing is kept as the separate shareBillTextOnWhatsApp feature.
 */
fun shareBillOnWhatsApp(
    context: Context,
    billWithItems: BillWithItems,
    profile: RestaurantProfileEntity?
) {
    // PDF generation (including review QR via ZXing) is CPU/IO heavy — run off main thread
    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
        try {
            val pdfGenerator = InvoicePDFGenerator(context)
            val pdfFile = pdfGenerator.generatePDF(billWithItems, profile)
            val pdfUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val rawPhone = billWithItems.bill.customerWhatsapp
            val digits = rawPhone?.replace(Regex("[^0-9]"), "") ?: ""
            val formattedPhone = when {
                digits.length == 10 -> "91$digits"
                digits.length > 10 -> digits
                else -> null
            }

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (formattedPhone != null) {
                    copyTextToClipboard(context, "WhatsApp Number", "+$formattedPhone")
                    if (tryJidWhatsApp(context, formattedPhone, pdfUri)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_whatsapp_search_number_copied),
                            Toast.LENGTH_LONG
                        ).show()
                        return@withContext
                    }
                    if (launchWhatsAppPdfSearchFallback(context, formattedPhone, pdfUri)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_whatsapp_search_number_copied),
                            Toast.LENGTH_LONG
                        ).show()
                        return@withContext
                    }
                }
                launchInvoiceShare(context, pdfUri)
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    UserMessageSanitizer.sanitize(e, "Unable to share invoice."),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

private fun tryJidWhatsApp(context: Context, phone: String, pdfUri: android.net.Uri): Boolean {
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    for (pkg in packages) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            context.grantUriPermission(pkg, pdfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra("jid", "$phone@s.whatsapp.net")
                clipData = android.content.ClipData.newRawUri("Invoice PDF", pdfUri)
                `package` = pkg
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            TrustedExternalAppReturn.mark(context)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            android.util.Log.w(UTILS_TAG, "WhatsApp direct share failed for package=$pkg", e)
        }
    }
    return false
}

private fun launchWhatsAppPdfSearchFallback(
    context: Context,
    phone: String,
    pdfUri: android.net.Uri
): Boolean {
    android.util.Log.d(UTILS_TAG, "Falling back to WhatsApp PDF picker for phone suffix=${phone.takeLast(4)}")
    return launchWhatsAppPdfPicker(context, pdfUri)
}

private fun launchWhatsAppPdfPicker(context: Context, pdfUri: android.net.Uri): Boolean {
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    for (pkg in packages) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            context.grantUriPermission(pkg, pdfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                clipData = android.content.ClipData.newRawUri("Invoice PDF", pdfUri)
                `package` = pkg
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            TrustedExternalAppReturn.mark(context)
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            android.util.Log.w(UTILS_TAG, "WhatsApp picker share failed for package=$pkg", e)
        }
    }
    return false
}

private fun launchInvoiceShare(
    context: Context,
    pdfUri: android.net.Uri
) {
    val baseIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        clipData = android.content.ClipData.newRawUri("Invoice PDF", pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(Intent.createChooser(baseIntent, "Share Invoice"))
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
            val bytes = InvoiceFormatter.formatForThermalPrinter(billWithItems, profile)
            printerManager.printBytes(bytes)
        } else {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.toast_printer_opening_pdf), Toast.LENGTH_SHORT).show()
                openBillToPrint(context, billWithItems, profile)
            }
        }
    }
}
