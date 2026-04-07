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
            // Two-phase share — works for saved and unsaved contacts.
            // Phase 1: open WhatsApp to the contact's chat via wa.me deep link.
            // Phase 2: fire PDF share targeting WhatsApp; user picks the chat from Phase 1.
            val waLaunched = tryWhatsAppDeepLink(context, formattedPhone)
            if (waLaunched) {
                launchPdfShareToWhatsApp(context, pdfUri)
                android.widget.Toast.makeText(
                    context,
                    "Select the chat from the share sheet to send the PDF.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                // WhatsApp not installed — fall back to generic share
                launchInvoiceShare(context, pdfUri, preferWhatsApp = false)
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
 * Opens WhatsApp directly to the chat for [phone] using the wa.me deep link.
 * Works for both saved and unsaved contacts.
 * Returns true if WhatsApp handled the intent, false if it is not installed.
 */
private fun tryWhatsAppDeepLink(context: Context, phone: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW,
            android.net.Uri.parse("https://wa.me/$phone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Launches a PDF share intent locked to WhatsApp so the user can attach the invoice
 * PDF in the chat they just opened via [tryWhatsAppDeepLink].
 */
private fun launchPdfShareToWhatsApp(context: Context, pdfUri: android.net.Uri) {
    val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
    val pdfIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        clipData = android.content.ClipData.newRawUri("Invoice PDF", pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    for (pkg in packages) {
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            context.startActivity(pdfIntent.apply { `package` = pkg })
            return
        } catch (_: Exception) {}
    }
    // WhatsApp not found — show generic share sheet for PDF
    context.startActivity(Intent.createChooser(pdfIntent, "Share Invoice PDF"))
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
