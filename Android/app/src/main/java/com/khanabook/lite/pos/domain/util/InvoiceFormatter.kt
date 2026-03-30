package com.khanabook.lite.pos.domain.util

import android.util.Log
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceFormatter {

    private const val TAG = "InvoiceFormatter"

    
    private val ESC: Byte = 0x1B
    private val GS: Byte  = 0x1D
    private val RESET      = byteArrayOf(ESC, 0x40) 
    private val BOLD_ON    = byteArrayOf(ESC, 0x45, 0x01)
    private val BOLD_OFF   = byteArrayOf(ESC, 0x45, 0x00)
    private val ALIGN_LEFT  = byteArrayOf(ESC, 0x61, 0x00)
    private val ALIGN_CENTER= byteArrayOf(ESC, 0x61, 0x01)
    private val LARGE_FONT = byteArrayOf(GS, 0x21, 0x11) 
    private val NORMAL_FONT= byteArrayOf(GS, 0x21, 0x00)
    private val CUT_PAPER  = byteArrayOf(GS, 0x56, 0x42, 0x00) 

    private fun resolveCurrency(profile: RestaurantProfileEntity?): String {
        return if (profile?.currency == "INR" || profile?.currency == "Rupee") "Rs." else profile?.currency ?: ""
    }

    private fun formatMoney(amount: String): String {
        return try {
            BigDecimal(amount)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
        } catch (e: Exception) {
            "0.00"
        }
    }

    fun formatForThermalPrinter(bill: BillWithItems, profile: RestaurantProfileEntity?): ByteArray {
        val charsPerLine = if (profile?.paperSize == "80mm") 48 else 32
        val currency = resolveCurrency(profile)
        val isGst = profile?.gstEnabled == true
        
        val width = charsPerLine
        val line = "-".repeat(width)
        val doubleLine = "=".repeat(width)

        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { 
            out.addAll(text.toByteArray(java.nio.charset.Charset.forName("GBK")).toList())
        }

        add(RESET)
        add(ALIGN_CENTER)
        
        // 1. Logo (if enabled)
        if (profile?.includeLogoInPrint == true && !profile.logoPath.isNullOrBlank()) {
            try {
                val bitmap = BitmapFactory.decodeFile(profile.logoPath)
                if (bitmap != null) {
                    try {
                        add(decodeBitmapToESC_POS(bitmap, if(width > 40) 384 else 256))
                        add("\n")
                    } finally {
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error printing logo", e)
            }
        }

        // 2. Header - Center Bold
        add(BOLD_ON)
        add(LARGE_FONT)
        add("${profile?.shopName ?: "BIRYANIWALE ANNA"}\n".uppercase())
        add(NORMAL_FONT)
        add(BOLD_OFF)
        
        // Address & Tax Info
        profile?.shopAddress?.takeIf { it.isNotBlank() }?.let { add("$it\n") }
        profile?.fssaiNumber?.takeIf { it.isNotBlank() }?.let { add("FSSAI: $it\n") }
        profile?.gstin?.takeIf { it.isNotBlank() }?.let { add("GSTIN: $it\n") }
        add("$line\n")

        // 3. Transaction Details (Left/Right split)
        add(ALIGN_LEFT)
        val billNo = "Bill: ${bill.bill.lifetimeOrderId}"
        val dateStr = DateUtils.formatDateOnly(bill.bill.createdAt)
        
        // Customer Masking
        val rawCust = bill.bill.customerName?.takeIf { it.isNotBlank() } ?: "Guest"
        val rawPhone = bill.bill.customerWhatsapp?.takeIf { it.isNotBlank() } ?: ""
        val shouldMask = profile?.maskCustomerPhone ?: true
        val displayPhone = if (shouldMask && rawPhone.length >= 10) {
            rawPhone.take(2) + "XXXXXX" + rawPhone.takeLast(2)
        } else {
            rawPhone
        }
        
        add(formatRow(billNo, "Date: $dateStr", width))
        add(formatRow("Cust: $rawCust", displayPhone, width))
        add("$line\n")

        // 4. Itemized Table (80mm optimization)
        // Give the removed HSN width entirely to the item name column
        val itemW = if (width > 40) 24 else 12
        val qtyW = 4
        val rateW = if (width > 40) 8 else 7
        val amtW = width - itemW - qtyW - rateW - 3 // 3 gaps

        val tableHeader = String.format("%-${itemW}s %${qtyW}s %${rateW}s %${amtW}s\n", "ITEM", "QTY", "RATE", "AMT")
        add(BOLD_ON)
        add(tableHeader)
        add(BOLD_OFF)
        add("$line\n")

        for (item in bill.items) {
            val name = item.itemName.uppercase()
            
            // Wrapping logic
            val itemLines = wrapText(name, itemW)
            val rateStr = formatMoney(item.price).take(rateW)
            val amtStr = formatMoney(item.itemTotal).take(amtW)

            add(String.format("%-${itemW}s %${qtyW}s %${rateW}s %${amtW}s\n", 
                itemLines[0], item.quantity, rateStr, amtStr))
            
            for (i in 1 until itemLines.size) {
                add(String.format("%-${itemW}s\n", itemLines[i]))
            }
        }
        add("$line\n")

        // 5. Financial Summary
        add(formatRow("Sub-total:", formatMoney(bill.bill.subtotal), width))
        
        if (isGst) {
            val halfGstAmt = formatMoney(bill.bill.cgstAmount)
            add(formatRow("CGST (2.5%):", halfGstAmt, width))
            add(formatRow("SGST (2.5%):", halfGstAmt, width))
        }
        
        add(BOLD_ON)
        add(LARGE_FONT)
        add(formatRow("NET AMT:", "$currency ${formatMoney(bill.bill.totalAmount)}", width))
        add(NORMAL_FONT)
        add(BOLD_OFF)
        add("$doubleLine\n")

        // 6. Payment & QR
        add("Payment Mode : ${bill.bill.paymentMode.uppercase()}\n")
        
        if (profile?.upiHandle?.isNotBlank() == true) {
            try {
                val amount = BigDecimal(bill.bill.totalAmount).toDouble()
                val qrBitmap = com.khanabook.lite.pos.domain.manager.QrCodeManager.generateUpiQr(
                    profile.upiHandle ?: "", 
                    profile.shopName ?: "RESTAURANT", 
                    amount, 
                    256
                )
                qrBitmap?.let {
                    add(ALIGN_CENTER)
                    add("\n")
                    add(decodeBitmapToESC_POS(it, 256))
                    add("\nSCAN TO PAY\n")
                    it.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error printing UPI QR", e)
            }
        }

        if (profile?.reviewUrl?.isNotBlank() == true) {
            try {
                val reviewQrBitmap = com.khanabook.lite.pos.domain.manager.QrCodeManager.generateQr(
                    profile.reviewUrl ?: "",
                    256
                )
                reviewQrBitmap?.let {
                    add(ALIGN_CENTER)
                    add("\n")
                    add(decodeBitmapToESC_POS(it, 256))
                    add("\nRATE US / FEEDBACK\n")
                    it.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error printing Review QR", e)
            }
        }

        // 7. Footer
        add(ALIGN_CENTER)
        add("Thank you! Visit again.\n")
        if (profile?.showBranding != false) {
            add(BOLD_ON)
            add("Powered by KhanaBook\n")
            add(BOLD_OFF)
        }
        add("\n\n\n\n") // Line feeds for clean cut
        add(CUT_PAPER)

        return out.toByteArray()
    }

    private fun wrapText(text: String, width: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        for (word in words) {
            if (currentLine.length + word.length + 1 <= width) {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return if (lines.isEmpty()) listOf("") else lines
    }

    fun formatForWhatsApp(bill: BillWithItems, profile: RestaurantProfileEntity?): String {
        val sb = StringBuilder()
        val width = if (profile?.paperSize == "80mm") 42 else 32
        val line = "-".repeat(width)
        
        val currency = if (profile?.currency == "INR" || profile?.currency == "Rupee") "₹" else profile?.currency ?: ""
        
        sb.append("🏛️ *${profile?.shopName?.uppercase() ?: "RESTAURANT"}*\n")
        if (!profile?.shopAddress.isNullOrBlank()) sb.append("📍 ${profile?.shopAddress}\n")
        if (!profile?.whatsappNumber.isNullOrBlank()) sb.append("📞 Contact: ${profile?.whatsappNumber}\n")
        
        val title = if (profile?.gstEnabled == true) "TAX INVOICE" else "INVOICE"
        sb.append("\n🧾 *--- $title ---*\n")
        
        sb.append("🔢 *Bill #:* ${bill.bill.lifetimeOrderId}\n")
        val formattedDate = com.khanabook.lite.pos.domain.util.DateUtils.formatDisplay(bill.bill.createdAt)
        sb.append("📅 *Date:* ${formattedDate}\n")
        
        sb.append("👤 *Customer:* ${bill.bill.customerName?.takeIf { it.isNotBlank() && it != "Walking Customer" } ?: "Guest"}\n")
        
        sb.append("\n📦 *ORDER SUMMARY*\n")
        sb.append("$line\n")
        for (item in bill.items) {
            val name = if (item.variantName != null) "${item.itemName} (${item.variantName})" else item.itemName
            sb.append("🔹 *${name.uppercase()}*\n")
            sb.append("   ${item.quantity} x $currency${formatMoney(item.price)} = $currency${formatMoney(item.itemTotal)}\n")
        }
        sb.append("$line\n")
        
        sb.append("💵 *Subtotal: $currency${formatMoney(bill.bill.subtotal)}*\n")
        
        if (profile?.gstEnabled == true) {
            val halfGst = try {
                BigDecimal(bill.bill.gstPercentage)
                    .divide(BigDecimal("2"), 2, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString()
            } catch (e: Exception) { "0" }

            val cgst = try {
                BigDecimal(bill.bill.cgstAmount)
                    .setScale(2, RoundingMode.HALF_UP).toPlainString()
            } catch (e: Exception) { "0.00" }

            val sgst = try {
                BigDecimal(bill.bill.sgstAmount)
                    .setScale(2, RoundingMode.HALF_UP).toPlainString()
            } catch (e: Exception) { "0.00" }

            sb.append("   CGST ($halfGst%): $currency$cgst\n")
            sb.append("   SGST ($halfGst%): $currency$sgst\n")
        }

        if (profile?.gstEnabled == false) {
            val customAmt = try {
                BigDecimal(bill.bill.customTaxAmount)
            } catch (e: Exception) { BigDecimal.ZERO }

            if (customAmt.compareTo(BigDecimal.ZERO) > 0) {
                val taxLabel = profile.customTaxName?.takeIf { it.isNotBlank() } ?: "Tax"
                sb.append("   $taxLabel: $currency${formatMoney(bill.bill.customTaxAmount)}\n")
            }
        }
        
        sb.append("\n💰 *TOTAL AMOUNT: $currency${formatMoney(bill.bill.totalAmount)}*\n")
        sb.append("$line\n")
        sb.append("💳 *Payment:* ${
            com.khanabook.lite.pos.domain.model.PaymentMode
                .fromDbValue(bill.bill.paymentMode).displayLabel
        }\n")
        
        if (!profile?.reviewUrl.isNullOrBlank()) {
            sb.append("\n⭐ *Rate Us / Feedback:* ${profile?.reviewUrl}\n")
        }

        sb.append("\nThank you! Visit Again 🙏\n")
        if (profile?.showBranding != false) {
            sb.append("_Software by KhanaBook_")
        }
        
        return sb.toString()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) {
            Log.w(TAG, "Text '$text' is longer than width $width and will be truncated.")
            return text.take(width)
        }
        val padding = maxOf(0, (width - text.length) / 2)
        return " ".repeat(padding) + text
    }

    private fun formatRow(label: String, value: String, width: Int): String {
        val spaceCount = width - label.length - value.length
        return if (spaceCount > 0) label + " ".repeat(spaceCount) + value + "\n"
        else "$label\n${" ".repeat(maxOf(0, width - value.length))}$value\n"
    }

    
    private fun decodeBitmapToESC_POS(bitmap: Bitmap, maxWidth: Int): ByteArray {
        var scaledBitmap: Bitmap? = null
        try {
            
            val scale = maxWidth.toFloat() / bitmap.width.toFloat()
            val targetHeight = (bitmap.height * scale).toInt()
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, targetHeight, true)

            val width = scaledBitmap!!.width
            val height = scaledBitmap.height
            val bytesWidth = (width + 7) / 8
            
            val data = mutableListOf<Byte>()
            
            
            data.add(0x1D.toByte())
            data.add(0x76.toByte())
            data.add(0x30.toByte())
            data.add(0x00.toByte())
            
            data.add((bytesWidth % 256).toByte()) 
            data.add((bytesWidth / 256).toByte()) 
            data.add((height % 256).toByte())    
            data.add((height / 256).toByte())    

            for (y in 0 until height) {
                for (x in 0 until bytesWidth) {
                    var bite = 0
                    for (b in 0 until 8) {
                        val pixelX = x * 8 + b
                        if (pixelX < width) {
                            val pixel = scaledBitmap.getPixel(pixelX, y)
                            val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                            if (gray < 128) { 
                                bite = bite or (0x80 shr b)
                            }
                        }
                    }
                    data.add(bite.toByte())
                }
            }
            return data.toByteArray()
        } finally {
            scaledBitmap?.recycle()
        }
    }
}


