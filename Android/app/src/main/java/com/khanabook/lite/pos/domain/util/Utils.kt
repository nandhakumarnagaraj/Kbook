package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.manager.InvoicePDFGenerator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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
 * Handles both saved and unsaved numbers using a smart fallback mechanism.
 */
fun shareBillOnWhatsApp(
    context: Context,
    billWithItems: BillWithItems,
    profile: RestaurantProfileEntity?
) {
    try {
        // 1. Generate PDF in cache for sharing
        val pdfGenerator = InvoicePDFGenerator(context)
        val pdfFile = pdfGenerator.generatePDF(billWithItems, profile)
        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )

        // 2. Normalize customer phone number
        val rawPhone = billWithItems.bill.customerWhatsapp
        val digits = rawPhone?.replace(Regex("[^0-9]"), "") ?: ""
        val formattedPhone = when {
            digits.length == 10 -> "91$digits" // Default to India prefix if 10 digits
            digits.length > 10 -> digits
            else -> null
        }

        // 3. Grant explicit URI permissions to WhatsApp packages
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                context.grantUriPermission(pkg, pdfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        if (formattedPhone != null) {
            val hasContactPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasContactPerm) {
                // Reliable path: save temp contact → jid + PDF → delete contact.
                // Saving the contact ensures WhatsApp can resolve the number even if
                // it has never been chatted with before.
                val rawContactUri = insertTempContact(context, formattedPhone, billWithItems.bill.customerName)
                val sent = tryJidWhatsApp(context, formattedPhone, pdfUri)
                // Delete temp contact after 3 s — WhatsApp has already opened the chat by then.
                rawContactUri?.let { uri ->
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    }, 3000)
                }
                if (!sent) launchInvoiceShare(context, pdfUri, preferWhatsApp = true)
            } else {
                // No contact permission — fallback: copy number + WhatsApp picker with PDF.
                copyTextToClipboard(context, "WhatsApp Number", formattedPhone)
                val sent = launchWhatsAppWithPdf(context, pdfUri)
                if (!sent) launchInvoiceShare(context, pdfUri, preferWhatsApp = false)
                android.widget.Toast.makeText(
                    context,
                    "Number copied — paste it in the search bar to find the contact.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // No phone number — generic share
            launchInvoiceShare(context, pdfUri, preferWhatsApp = false)
        }

    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            UserMessageSanitizer.sanitize(e, "Unable to share invoice."),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun launchInvoiceShare(
    context: Context,
    pdfUri: android.net.Uri,
    preferWhatsApp: Boolean
) {
    val baseIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        clipData = android.content.ClipData.newRawUri("Invoice PDF", pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (preferWhatsApp) {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                context.startActivity(baseIntent.apply { `package` = pkg })
                return
            } catch (_: Exception) {}
        }
    }

    context.startActivity(Intent.createChooser(baseIntent, "Share Invoice"))
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

/**
 * Inserts a minimal temporary contact for [phone] so WhatsApp can resolve the number
 * via its Android-contacts sync. Returns the raw-contact URI for later deletion.
 */
private fun insertTempContact(context: Context, phone: String, name: String?): android.net.Uri? {
    val displayName = name?.ifBlank { phone } ?: phone
    val ops = arrayListOf(
        android.content.ContentProviderOperation
            .newInsert(android.provider.ContactsContract.RawContacts.CONTENT_URI)
            .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build(),
        android.content.ContentProviderOperation
            .newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(android.provider.ContactsContract.Data.MIMETYPE,
                android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
            .build(),
        android.content.ContentProviderOperation
            .newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(android.provider.ContactsContract.Data.MIMETYPE,
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, "+$phone")
            .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE,
                android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            .build()
    )
    return try {
        context.contentResolver.applyBatch(android.provider.ContactsContract.AUTHORITY, ops)[0].uri
    } catch (_: Exception) { null }
}

/**
 * Opens WhatsApp directly to the chat for [phone] using the jid extra with PDF attached.
 * Requires the contact to be saved in Android contacts for reliable routing.
 * Returns true if WhatsApp accepted the intent.
 */
private fun tryJidWhatsApp(context: Context, phone: String, pdfUri: android.net.Uri): Boolean {
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    for (pkg in packages) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                putExtra("jid", "$phone@s.whatsapp.net")
                `package` = pkg
                clipData = android.content.ClipData.newRawUri("Invoice PDF", pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {}
    }
    return false
}

/**
 * Opens WhatsApp's contact picker with the PDF already attached (fallback for no contact permission).
 * Returns true if WhatsApp accepted the intent.
 */
private fun launchWhatsAppWithPdf(context: Context, pdfUri: android.net.Uri): Boolean {
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        clipData = android.content.ClipData.newRawUri("Invoice PDF", pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    for (pkg in packages) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            context.startActivity(intent.apply { `package` = pkg })
            return true
        } catch (_: Exception) {}
    }
    return false
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
        ?: kotlinx.coroutines.GlobalScope

    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        if (!printerManager.isConnected() && !profile.printerMac.isNullOrBlank()) {
            printerManager.connect(profile.printerMac)
        }
        
        if (printerManager.isConnected()) {
            val bytes = InvoiceFormatter.formatForThermalPrinter(billWithItems, profile)
            printerManager.printBytes(bytes)
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Printer not connected. Opening PDF...", Toast.LENGTH_SHORT).show()
                openBillToPrint(context, billWithItems, profile)
            }
        }
    }
}
