package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import java.io.File
import java.io.FileOutputStream

private const val TAG = "InvoicePDFGenerator"

class InvoicePDFGenerator(private val context: Context) {

    private fun formatAmount(amount: String): String {
        return try {
            java.math.BigDecimal(amount)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString()
        } catch (e: Exception) {
            "0.00"
        }
    }

    /**
     * Pre-calculates how many lines an item name will wrap to given the column width and paint.
     * Must be called with the same Paint/textSize that will be used when drawing.
     */
    private fun countWrappedLines(name: String, colWidth: Float, paint: Paint): Int {
        var count = 0
        var currentLine = ""
        name.split(" ").forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= colWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) count++
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) count++
        return maxOf(count, 1)
    }

    fun generatePDF(
            bill: BillWithItems,
            profile: RestaurantProfileEntity?,
            isDigital: Boolean = true
    ): File {
        val pdfDocument = PdfDocument()

        val is80mm = profile?.paperSize == "80mm"
        val pageWidth = if (is80mm) 226 else 164

        var logoBitmap: Bitmap? = null
        try {
            logoBitmap = profile?.logoPath?.let { path ->
                try { BitmapFactory.decodeFile(path) } catch (e: Exception) { null }
            }

            val includeLogo      = profile?.includeLogoInPrint == true
            val includeCustomerWa = profile?.printCustomerWhatsapp == true

            val bodySize     = if (is80mm) 7f else 6f
            val itemColWidth = if (is80mm) 100f else 65f

            // ── Pre-calculate accurate item section height ──────────────────────
            val measurePaint = Paint().apply { textSize = bodySize }
            var itemSectionHeight = 14  // 12f gap before first item + 2f trailing spacer
            bill.items.forEach { item ->
                val lines = countWrappedLines(item.itemName.uppercase(), itemColWidth, measurePaint)
                itemSectionHeight += lines * 9 + 12   // 9f per line + 2f spacer + 10f row gap
            }

            val logoH    = if (logoBitmap != null && includeLogo) 60 else 0
            val waH      = if (includeCustomerWa && !bill.bill.customerWhatsapp.isNullOrBlank()) 12 else 0
            val fssaiH   = if (!profile?.fssaiNumber.isNullOrBlank()) 10 else 0
            val gstinH   = if (profile?.gstEnabled == true && !profile.gstin.isNullOrBlank()) 11 else 0
            val shopWaH  = if (!profile?.whatsappNumber.isNullOrBlank()) 9 else 0
            val gstTaxH  = if (profile?.gstEnabled == true) 22 else 0

            val headerH  = 145 + logoH + waH + fssaiH + gstinH + shopWaH
            val summaryH = 100 + gstTaxH
            val upiQrH   = if (profile?.upiHandle?.isNotBlank() == true) 110 else 0
            val reviewQrH = if (profile?.reviewUrl?.isNotBlank() == true) 110 else 0
            val footerH  = 80
            val pageHeight = headerH + itemSectionHeight + summaryH + upiQrH + reviewQrH + footerH + 30 // 30px safety margin

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page     = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint          = Paint()
            val normalTypeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val boldTypeface   = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            var y = 14f

            // ── Logo ─────────────────────────────────────────────────────────────
            if (logoBitmap != null && includeLogo) {
                val scaledW = if (is80mm) 90f else 70f
                val scaledH = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * scaledW
                val left    = (pageWidth - scaledW) / 2f
                canvas.drawBitmap(logoBitmap, null, RectF(left, y, left + scaledW, y + scaledH), paint)
                y += scaledH + 10f
            }

            val colorPrimary = if (isDigital) Color.parseColor("#2E150B") else Color.BLACK
            val colorText    = Color.BLACK
            val mainTitleSize = if (is80mm) 12f else 10f
            val subTitleSize  = if (is80mm) 7f  else 6f

            // ── Shop name ─────────────────────────────────────────────────────────
            paint.color     = colorPrimary
            paint.typeface  = boldTypeface
            paint.textSize  = mainTitleSize
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                profile?.shopName?.uppercase() ?: "RESTAURANT",
                (pageWidth / 2).toFloat(), y, paint
            )
            y += 11f

            // ── Address (max 2 lines) ─────────────────────────────────────────────
            paint.color    = Color.parseColor("#757575")
            paint.typeface = normalTypeface
            paint.textSize = subTitleSize
            val addr = profile?.shopAddress ?: ""
            if (addr.isNotBlank()) {
                val lines = if (addr.length > 35) {
                    val mid = addr.lastIndexOf(",", 35).takeIf { it != -1 }
                        ?: addr.lastIndexOf(" ", 35).takeIf { it != -1 }
                        ?: 35
                    listOf(addr.substring(0, mid + 1).trim(), addr.substring(mid + 1).trim())
                } else {
                    listOf(addr)
                }
                lines.take(2).filter { it.isNotBlank() }.forEach { line ->
                    canvas.drawText(line, (pageWidth / 2).toFloat(), y, paint)
                    y += 9f
                }
            }

            // ── FSSAI / GSTIN / Contact — centered label+value pairs ──────────────
            paint.color    = colorText
            paint.textSize = subTitleSize
            paint.textAlign = Paint.Align.LEFT

            fun drawCenteredLabelValue(label: String, value: String) {
                paint.typeface = boldTypeface
                val lw = paint.measureText(label)
                paint.typeface = normalTypeface
                val vw = paint.measureText(value)
                val sx = (pageWidth - (lw + vw)) / 2f
                paint.typeface = boldTypeface
                canvas.drawText(label, sx, y, paint)
                paint.typeface = normalTypeface
                canvas.drawText(value, sx + lw, y, paint)
            }

            if (!profile?.fssaiNumber.isNullOrBlank()) {
                drawCenteredLabelValue("FSSAI: ", profile?.fssaiNumber ?: "")
                y += 9f
            }
            if (profile?.gstEnabled == true && !profile.gstin.isNullOrBlank()) {
                drawCenteredLabelValue("GST NO: ", profile.gstin)
                y += 9f
            }
            if (!profile?.whatsappNumber.isNullOrBlank()) {
                drawCenteredLabelValue("Contact: ", profile?.whatsappNumber ?: "")
                y += 9f
            }

            // ── Divider + INVOICE title ───────────────────────────────────────────
            y += 4f
            paint.strokeWidth = 0.5f
            paint.color       = colorText
            canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)
            y += 12f

            if (isDigital) {
                paint.color = Color.argb(25,
                    Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary))
                canvas.drawRect(5f, y - 9f, (pageWidth - 5).toFloat(), y + 4f, paint)
            }
            paint.color     = colorPrimary
            paint.typeface  = boldTypeface
            paint.textSize  = 9f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                if (profile?.gstEnabled == true) "TAX INVOICE" else "INVOICE",
                (pageWidth / 2).toFloat(), y, paint
            )
            y += 6f
            paint.color = colorText
            canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

            // ── Bill details row (BILL NO left, DATE right) ───────────────────────
            y += 13f
            paint.textSize  = bodySize
            paint.textAlign = Paint.Align.LEFT

            paint.typeface  = boldTypeface
            val billLabel   = "BILL NO: "
            val billLabelW  = paint.measureText(billLabel)
            canvas.drawText(billLabel, 5f, y, paint)
            paint.typeface  = normalTypeface
            canvas.drawText("${bill.bill.lifetimeOrderId}", 5f + billLabelW, y, paint)

            val dateStr = com.khanabook.lite.pos.domain.util.DateUtils.formatDateOnly(bill.bill.createdAt)
            paint.typeface  = boldTypeface
            val dateLabelW  = paint.measureText("DATE: ")
            paint.typeface  = normalTypeface
            val dateValueW  = paint.measureText(dateStr)
            val dateRight   = (pageWidth - 5).toFloat()
            paint.typeface  = boldTypeface
            canvas.drawText("DATE: ", dateRight - dateValueW - dateLabelW, y, paint)
            paint.typeface  = normalTypeface
            canvas.drawText(dateStr, dateRight - dateValueW, y, paint)

            // ── Customer row (NAME left, PHONE right) ────────────────────────────
            y += 10f
            paint.textAlign = Paint.Align.LEFT
            paint.typeface  = boldTypeface
            val custLabel   = "CUSTOMER: "
            val custLabelW  = paint.measureText(custLabel)
            canvas.drawText(custLabel, 5f, y, paint)
            paint.typeface  = normalTypeface

            val rawCust    = (bill.bill.customerName ?: "GUEST").uppercase()
            val maxCustW   = if (is80mm) 90f else 55f
            var dispCust   = rawCust
            if (paint.measureText(dispCust) > maxCustW) {
                while (dispCust.isNotEmpty() && paint.measureText("$dispCust...") > maxCustW)
                    dispCust = dispCust.dropLast(1)
                dispCust += "..."
            }
            canvas.drawText(dispCust, 5f + custLabelW, y, paint)

            val rawPhone = bill.bill.customerWhatsapp ?: ""
            if (rawPhone.isNotBlank()) {
                val shouldMask   = profile?.maskCustomerPhone ?: true
                val displayPhone = if (shouldMask && rawPhone.length >= 10) {
                    rawPhone.take(2) + "XXXXXX" + rawPhone.takeLast(2)
                } else rawPhone

                paint.typeface  = boldTypeface
                val phoneLabelW = paint.measureText("PHONE: ")
                paint.typeface  = normalTypeface
                val phoneValW   = paint.measureText(displayPhone)
                val phoneRight  = (pageWidth - 5).toFloat()
                paint.typeface  = boldTypeface
                canvas.drawText("PHONE: ", phoneRight - phoneValW - phoneLabelW, y, paint)
                paint.typeface  = normalTypeface
                canvas.drawText(displayPhone, phoneRight - phoneValW, y, paint)
            }

            // ── Table header ──────────────────────────────────────────────────────
            val amtRightX  = (pageWidth - 5).toFloat()
            val qtyRightX  = if (is80mm) 176f else 122f
            val rateRightX = if (is80mm) 141f else 95f

            y += 12f
            paint.strokeWidth = 0.5f
            canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)
            y += 10f

            paint.typeface  = boldTypeface
            paint.textSize  = bodySize
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("ITEM", 5f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("RATE", rateRightX, y, paint)
            canvas.drawText("QTY",  qtyRightX,  y, paint)
            canvas.drawText("AMT",  amtRightX,  y, paint)
            y += 4f
            paint.strokeWidth = 0.3f
            canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

            // ── Items ─────────────────────────────────────────────────────────────
            paint.typeface = normalTypeface
            paint.textSize = bodySize
            y += 12f

            bill.items.forEachIndexed { index, item ->
                paint.color     = colorText
                paint.textAlign = Paint.Align.LEFT
                val displayName = item.itemName.uppercase()

                // Word-wrap item name into itemColWidth
                val itemLines   = mutableListOf<String>()
                var currentLine = ""
                displayName.split(" ").forEach { word ->
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) <= itemColWidth) {
                        currentLine = testLine
                    } else {
                        if (currentLine.isNotEmpty()) itemLines.add(currentLine)
                        currentLine = word
                    }
                }
                if (currentLine.isNotEmpty()) itemLines.add(currentLine)

                val priceY = y  // RATE/QTY/AMT always on the same row as first name line
                itemLines.forEach { line ->
                    canvas.drawText(line, 5f, y, paint)
                    y += 9f
                }

                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatAmount(item.price),     rateRightX, priceY, paint)
                canvas.drawText("${item.quantity}",           qtyRightX,  priceY, paint)
                canvas.drawText(formatAmount(item.itemTotal), amtRightX,  priceY, paint)

                y += 2f
                if (index < bill.items.size - 1) {
                    paint.color       = Color.parseColor("#EEEEEE")
                    paint.strokeWidth = 0.3f
                    canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)
                }
                y += 10f
            }

            // ── Summary separator ─────────────────────────────────────────────────
            y += 2f
            paint.color       = colorText
            paint.strokeWidth = 0.5f
            canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

            // Summary labels start at 55% width so they don't overlap item names
            val summaryLabelX = pageWidth * 0.52f

            y += 12f
            paint.typeface  = normalTypeface
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("SUB-TOTAL", summaryLabelX, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAmount(bill.bill.subtotal), amtRightX, y, paint)

            if (profile?.gstEnabled == true) {
                val gstPct = try {
                    java.math.BigDecimal(bill.bill.gstPercentage)
                        .divide(java.math.BigDecimal("2"), 1, java.math.RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString()
                } catch (e: Exception) { "2.5" }

                y += 10f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("CGST ($gstPct%)", summaryLabelX, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatAmount(bill.bill.cgstAmount), amtRightX, y, paint)

                y += 10f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("SGST ($gstPct%)", summaryLabelX, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatAmount(bill.bill.sgstAmount), amtRightX, y, paint)
            }

            y += 8f
            paint.strokeWidth = 0.3f
            canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

            // ── Net Amount box ────────────────────────────────────────────────────
            y += 10f
            val boxTop    = y
            val boxBottom = boxTop + 22f
            if (isDigital) {
                paint.color = colorPrimary
                canvas.drawRoundRect(5f, boxTop, (pageWidth - 5).toFloat(), boxBottom, 4f, 4f, paint)
                paint.color = Color.WHITE
            } else {
                paint.color       = colorText
                paint.strokeWidth = 0.8f
                canvas.drawLine(5f, boxTop, (pageWidth - 5).toFloat(), boxTop, paint)
                paint.color = colorText
            }

            val netTextY = boxTop + 15f   // baseline sits comfortably inside the box
            paint.typeface  = boldTypeface
            paint.textSize  = bodySize + 2f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("NET AMOUNT", 10f, netTextY, paint)

            val currencySymbol = when (profile?.currency) {
                "INR", "Rupee" -> "Rs."
                null -> ""
                else -> profile.currency
            }
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$currencySymbol ${formatAmount(bill.bill.totalAmount)}", (pageWidth - 10).toFloat(), netTextY, paint)

            y = boxBottom + 10f
            paint.color     = colorText
            paint.typeface  = normalTypeface
            paint.textSize  = bodySize
            paint.textAlign = Paint.Align.LEFT
            val paymentLabel = com.khanabook.lite.pos.domain.model.PaymentMode
                .fromDbValue(bill.bill.paymentMode).displayLabel.uppercase()
            canvas.drawText("PAYMENT: $paymentLabel", 5f, y, paint)

            // ── UPI QR ────────────────────────────────────────────────────────────
            if (profile?.upiHandle?.isNotBlank() == true) {
                y += 18f
                try {
                    val amount = try { java.math.BigDecimal(bill.bill.totalAmount).toDouble() } catch (e: Exception) { 0.0 }
                    val qrBitmap = QrCodeManager.generateUpiQr(
                        profile.upiHandle ?: "",
                        profile.shopName ?: "RESTAURANT",
                        amount, 200
                    )
                    qrBitmap?.let {
                        val qrSize = if (is80mm) 80f else 64f
                        val left   = (pageWidth - qrSize) / 2f
                        canvas.drawBitmap(it, null, RectF(left, y, left + qrSize, y + qrSize), paint)
                        y += qrSize + 8f
                        paint.textAlign = Paint.Align.CENTER
                        paint.textSize  = 6f
                        canvas.drawText("SCAN TO PAY", (pageWidth / 2).toFloat(), y, paint)
                        y += 6f
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to draw UPI QR", e)
                }
            }

            // ── Review QR ─────────────────────────────────────────────────────────
            if (profile?.reviewUrl?.isNotBlank() == true) {
                y += 18f
                try {
                    val reviewQrBitmap = QrCodeManager.generateQr(profile.reviewUrl ?: "", 200)
                    reviewQrBitmap?.let {
                        val qrSize = if (is80mm) 80f else 64f
                        val left   = (pageWidth - qrSize) / 2f
                        canvas.drawBitmap(it, null, RectF(left, y, left + qrSize, y + qrSize), paint)
                        y += qrSize + 8f
                        paint.textAlign = Paint.Align.CENTER
                        paint.textSize  = 6f
                        canvas.drawText("RATE US / FEEDBACK", (pageWidth / 2).toFloat(), y, paint)
                        y += 6f
                        it.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to draw Review QR", e)
                }
            }

            // ── Footer ────────────────────────────────────────────────────────────
            y += 16f
            paint.typeface  = boldTypeface
            paint.textSize  = 7f
            paint.textAlign = Paint.Align.CENTER
            if (isDigital) {
                paint.color = colorPrimary
                canvas.drawRect(5f, y - 9f, (pageWidth - 5).toFloat(), y + 4f, paint)
                paint.color = Color.WHITE
            }
            canvas.drawText("THANK YOU! VISIT AGAIN.", (pageWidth / 2).toFloat(), y, paint)

            paint.color    = colorText
            paint.typeface = normalTypeface
            paint.textSize = bodySize
            y += 12f
            if (profile?.showBranding != false) {
                canvas.drawText("Powered by KhanaBook", (pageWidth / 2).toFloat(), y, paint)
                y += 10f
            }
            paint.strokeWidth = 0.5f
            canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

            pdfDocument.finishPage(page)

            val invoiceDir = File(context.cacheDir, "invoices")
            invoiceDir.mkdirs()
            val file = File(invoiceDir, "invoice_${bill.bill.lifetimeOrderId}.pdf")
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            return file

        } finally {
            logoBitmap?.recycle()
            pdfDocument.close()
        }
    }
}
