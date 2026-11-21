package com.khanabook.lite.pos.feature.billing.domain

import com.khanabook.lite.pos.core.util.AppAssetStore
import com.khanabook.lite.pos.core.util.DateUtils
import android.content.Context
import android.util.Log
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.getInvoiceNumberDisplay
import com.khanabook.lite.pos.feature.payments.domain.QrCodeManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    private fun decodeSampledBitmap(path: String, maxWidth: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0) return null
        opts.inSampleSize = maxOf(1, opts.outWidth / maxWidth)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun loadLogoBitmap(context: Context?, profile: RestaurantProfileEntity?, maxWidth: Int): Bitmap? {
        // Local-first: the logo is downloaded to a local file during profile sync
        // (see MasterSyncProcessor), so printing must never depend on the network.
        AppAssetStore.resolveAssetPath(profile?.logoPath)?.let { path ->
            val local = try { decodeSampledBitmap(path, maxWidth) } catch (_: Exception) { null }
            if (local != null) return local
        }

        // Fallback only when no local logo exists yet (e.g. first run before the
        // sync-time download completed). This is a best-effort online path, strictly
        // time-boxed to 2s so a slow network can never stall a receipt print —
        // printing proceeds without the logo rather than blocking the print path.
        val logoUrl = profile?.logoUrl?.takeIf { it.isNotBlank() }
        if (context != null && logoUrl != null) {
            return runBlocking(Dispatchers.IO) {
                try {
                    withTimeout(2_000) {
                        val request = ImageRequest.Builder(context)
                            .data(logoUrl)
                            .allowHardware(false)
                            .size(maxWidth, maxWidth)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                        val result = context.imageLoader.execute(request)
                        (result as? SuccessResult)?.drawable?.toBitmap()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "CDN logo fallback skipped (${e.javaClass.simpleName}) — printing without logo")
                    null
                }
            }
        }
        return null
    }

    /**
     * Same rule the PDF renderer applies before it draws the review QR
     * (see InvoicePDFGenerator.isValidReviewUrl): a non-http or host-less value is not
     * scannable, so it must not be encoded here either. Uses java.net.URI rather than
     * android.net.Uri so the receipt byte stream stays unit-testable on the JVM.
     */
    private fun isValidReviewUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val parsed = java.net.URI(url)
            parsed.scheme?.lowercase() in listOf("http", "https") &&
                !parsed.host.isNullOrBlank() &&
                parsed.host != "localhost" &&
                url.length > 10
        } catch (e: Exception) {
            false
        }
    }

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

    fun formatForThermalPrinter(
        bill: BillWithItems,
        profile: RestaurantProfileEntity?,
        context: Context? = null
    ): ByteArray {
        val is80mm = profile?.paperSize == "80mm"
        val width = if (is80mm) 40 else 32
        val leftPad = if (is80mm) "    " else "" // 4 spaces to center 40 chars on 48-char standard 80mm printer
        val currency = resolveCurrency(profile)
        // GST output follows the shop setting, not the bill's stored amounts: when the owner has
        // not enabled GST, no GST identity or tax lines may appear (pinned by GenerateBillTextTest,
        // "GSTIN must not leak when GST disabled"). The cost is that a bill issued while GST was
        // on, then reprinted after it was turned off, loses its tax breakdown while its stored
        // total still includes it - raised with the owner as BUG-036.
        val isGst = profile?.gstEnabled == true
        
        val line = leftPad + "-".repeat(width)
        val doubleLine = leftPad + "=".repeat(width)

        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { 
            out.addAll(text.toByteArray(java.nio.charset.Charset.forName("GBK")).toList())
        }

        add(RESET)
        add(ALIGN_CENTER)
        
        // Text invoice (the shop logo bitmap stays suppressed for ESC/POS compatibility).
        // The only raster this receipt emits is the review QR, encoded as GS v 0 by decodeBitmapToESC_POS.

        // 2. Header - Center Bold
        add(BOLD_ON)
        add(LARGE_FONT)
        add("${profile?.shopName ?: "BIRYANIWALE ANNA"}\n".uppercase())
        add(NORMAL_FONT)
        add(BOLD_OFF)
        
        // Address & Tax Info
        profile?.shopAddress?.takeIf { it.isNotBlank() }?.let { add("$it\n") }
        profile?.fssaiNumber?.takeIf { it.isNotBlank() }?.let { add("FSSAI: $it\n") }
        // GSTIN is a tax identity, not a shop detail: print it only when the shop has GST
        // enabled, exactly like the PDF copy (InvoicePDFGenerator uses the same rule).
        if (isGst) profile?.gstin?.takeIf { it.isNotBlank() }?.let { add("GSTIN: $it\n") }
        val invLabel = if (isGst) "TAX INVOICE NO" else "INVOICE NO"
        add("$invLabel: ${bill.bill.getInvoiceNumberDisplay()}\n")
        add("$line\n")
        // 3. Transaction Details (Left/Right split)
        add(ALIGN_LEFT)
        val displayOrder = bill.bill.dailyOrderDisplay.trim().ifEmpty { bill.bill.dailyOrderId.toString() }
        val billNo = "Bill: $displayOrder"
        val dateStr = DateUtils.formatDateOnly(bill.bill.createdAt)
        
        add(leftPad + formatRow(billNo, "Date: $dateStr", width))
        add("$line\n")

        // 4. Itemized Table (80mm optimization)
        // Give the removed HSN width entirely to the item name column
        val itemW = if (is80mm) 18 else 12
        val qtyW = 4
        val rateW = if (is80mm) 8 else 7
        val amtW = width - itemW - qtyW - rateW - 3 // 3 gaps

        val tableHeader = leftPad + String.format("%-${itemW}s %${qtyW}s %${rateW}s %${amtW}s\n", "ITEM", "QTY", "RATE", "AMT")
        add(BOLD_ON)
        add(tableHeader)
        add(BOLD_OFF)
        add("$line\n")

        for (item in bill.getConsolidatedItems()) {
            val name = if (!item.variantName.isNullOrBlank())
                "${item.itemName} (${item.variantName})".uppercase()
            else
                item.itemName.uppercase()

            // Wrapping logic
            val itemLines = wrapText(name, itemW)
            val rateStr = formatMoney(item.price).take(rateW)
            val amtStr = formatMoney(item.itemTotal).take(amtW)

            add(leftPad + String.format("%-${itemW}s %${qtyW}s %${rateW}s %${amtW}s\n", 
                itemLines[0], item.quantity, rateStr, amtStr))
            
            for (i in 1 until itemLines.size) {
                add(leftPad + String.format("%-${itemW}s\n", itemLines[i]))
            }
        }
        add("$line\n")

        // 5. Financial Summary
        add(leftPad + formatRow("Sub-total:", formatMoney(bill.bill.subtotal), width))
        
        if (isGst) {
            val gstPct = try {
                java.math.BigDecimal(bill.bill.gstPercentage)
                    .divide(java.math.BigDecimal("2"), 1, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString()
            } catch (e: Exception) { "2.5" }
            add(leftPad + formatRow("CGST ($gstPct%):", formatMoney(bill.bill.cgstAmount), width))
            add(leftPad + formatRow("SGST ($gstPct%):", formatMoney(bill.bill.sgstAmount), width))
        }
        
        // Custom tax only applies while GST is off (CartManager picks one or the other), and the
        // row appears only when this bill actually carries an amount.
        if (profile?.gstEnabled == false) {
            val customTaxAmt = try { java.math.BigDecimal(bill.bill.customTaxAmount) } catch (e: Exception) { java.math.BigDecimal.ZERO }
            if (customTaxAmt.compareTo(java.math.BigDecimal.ZERO) > 0) {
                val taxLabel = profile.customTaxName?.takeIf { it.isNotBlank() } ?: "Tax"
                add(leftPad + formatRow("$taxLabel:", formatMoney(bill.bill.customTaxAmount), width))
            }
        }

        add(BOLD_ON)
        add("$doubleLine\n")
        add(leftPad + formatRow("GRAND TOTAL:", "$currency ${formatMoney(bill.bill.totalAmount)}", width))
        add(BOLD_OFF)
        add("$doubleLine\n")

        // 6. Payment & QR
        add(leftPad + "Payment Mode : ${bill.bill.paymentMode.uppercase()}\n")

        val reviewUrl = profile?.reviewUrl?.takeIf { isValidReviewUrl(it) }
        if (reviewUrl != null) {
            add(ALIGN_CENTER)
            add("RATE US / FEEDBACK:\n")
            val qrDots = if (is80mm) 240 else 192
            val qr = QrCodeManager.generateQr(reviewUrl, qrDots)
            if (qr != null) {
                add(decodeBitmapToESC_POS(qr, qrDots, qrDots))
                qr.recycle()
            } else {
                // Encoder unavailable: still hand the customer a usable link rather than nothing.
                add("$reviewUrl\n")
            }
        }

        // 7. Footer
        add(ALIGN_CENTER)
        add("${profile?.invoiceFooter?.takeIf { it.isNotBlank() } ?: "Thank you! Visit again."}\n")
        if (profile?.showBranding != false) {
            add(BOLD_ON)
            add("Powered by KhanaBook\n")
            add(BOLD_OFF)
        }
        add("\n\n\n\n") // Line feeds for clean tear/cut
        if (is80mm) {
            add(CUT_PAPER)
        }

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

    
    private fun decodeBitmapToESC_POS(bitmap: Bitmap, maxWidth: Int, maxHeight: Int = 120): ByteArray {
        var scaledBitmap: Bitmap? = null
        try {
            val scale = minOf(
                maxWidth.toFloat() / bitmap.width.coerceAtLeast(1).toFloat(),
                maxHeight.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
            )
            val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

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
                            val alpha = (pixel shr 24) and 0xFF
                            // Transparent background (alpha < 128) must remain white paper (0)
                            if (alpha >= 128) {
                                val r = (pixel shr 16) and 0xFF
                                val g = (pixel shr 8) and 0xFF
                                val bVal = pixel and 0xFF
                                val gray = (r * 299 + g * 587 + bVal * 114) / 1000
                                if (gray < 160) {
                                    bite = bite or (0x80 shr b)
                                }
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


