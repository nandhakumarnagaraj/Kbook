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
 * Uses WhatsApp's official click-to-chat link so unsaved numbers open reliably
 * without READ_CONTACTS/WRITE_CONTACTS permission.
 *
 * Strategy:
 * 1. Open the exact WhatsApp chat with a pre-filled text invoice.
 * 2. If the number is missing/invalid, or the official link cannot be handled,
 *    fall back to a generic PDF share sheet.
 *
 * WhatsApp does not provide a guaranteed public Android API for sending a PDF
 * directly to an unsaved phone number, so the reliable targeted path is text.
 */
fun shareBillOnWhatsApp(
    context: Context,
    billWithItems: BillWithItems,
    profile: RestaurantProfileEntity?
) {
    try {
        val rawPhone = billWithItems.bill.customerWhatsapp
        val digits = rawPhone?.replace(Regex("[^0-9]"), "") ?: ""
        val formattedPhone = when {
            digits.length == 10 -> "91$digits" // Default to India prefix if 10 digits
            digits.length > 10 -> digits
            else -> null
        }

        if (formattedPhone != null) {
            val text = generateBillText(billWithItems, profile)
            if (openWhatsAppChatWithText(context, formattedPhone, text)) {
                return
            }
        }

        launchInvoicePdfShare(context, billWithItems, profile)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            UserMessageSanitizer.sanitize(e, "Unable to share invoice."),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun openWhatsAppChatWithText(context: Context, phone: String, text: String): Boolean {
    val url = "https://api.whatsapp.com/send?phone=$phone&text=${android.net.Uri.encode(text)}"
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

private fun launchInvoicePdfShare(
    context: Context,
    billWithItems: BillWithItems,
    profile: RestaurantProfileEntity?
) {
    val pdfGenerator = InvoicePDFGenerator(context)
    val pdfFile = pdfGenerator.generatePDF(billWithItems, profile)
    val pdfUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        pdfFile
    )

    launchInvoiceShare(context, pdfUri)
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
