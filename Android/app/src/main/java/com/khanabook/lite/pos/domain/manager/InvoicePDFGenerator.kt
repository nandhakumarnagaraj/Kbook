package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import java.io.File
import java.io.FileOutputStream

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

    fun generatePDF(
            bill: BillWithItems,
            profile: RestaurantProfileEntity?,
            isDigital: Boolean = true
    ): File {
        val pdfDocument = PdfDocument()

        // 1. Setup Page Dimensions
        val is80mm = profile?.paperSize == "80mm"
        val pageWidth = if (is80mm) 226 else 164

        // 2. Load Logo if exists
        var logoBitmap: Bitmap? = null
        try {
            logoBitmap = profile?.logoPath?.let { path ->
                try {
                    BitmapFactory.decodeFile(path)
                } catch (e: Exception) {
                    null
                }
            }

            // 3. Configuration
            val includeLogo = profile?.includeLogoInPrint == true
            val includeCustomerWhatsapp = profile?.printCustomerWhatsapp == true

            // 4. Calculate Heights
            val logoHeight = if (logoBitmap != null && includeLogo) 50 else 0
            val whatsappHeight =
                if (includeCustomerWhatsapp && !bill.bill.customerWhatsapp.isNullOrBlank()) 12
                else 0
            val fssaiHeight = if (!profile?.fssaiNumber.isNullOrBlank()) 12 else 0
            val gstinHeight = if (profile?.gstEnabled == true && !profile.gstin.isNullOrBlank()) 12 else 0
            val shopWaHeight = if (!profile?.whatsappNumber.isNullOrBlank()) 12 else 0
            
            val itemHeight = bill.items.size * 18
            val headerHeight = 180 + logoHeight + whatsappHeight + fssaiHeight + gstinHeight + shopWaHeight
            val summaryHeight = 140
            val taxHeight = if (profile?.gstEnabled == true) 50 else 0
            val upiQrHeight = if (profile?.upiHandle?.isNotBlank() == true) 100 else 0
            val reviewQrHeight = if (profile?.reviewUrl?.isNotBlank() == true) 100 else 0
            val footerHeight = 126
            val pageHeight = headerHeight + itemHeight + summaryHeight + taxHeight + upiQrHeight + reviewQrHeight + footerHeight

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint()
            val normalTypeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val monoTypeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)

            var y = 15f

            // 5. Draw Logo
            if (logoBitmap != null && includeLogo) {
                // Increase logo size further and scale based on paper width
                val scaledWidth = if (is80mm) 95f else 75f
                val scaledHeight =
                    (logoBitmap.height.toFloat() / logoBitmap.width.toFloat()) * scaledWidth
                val left = (pageWidth - scaledWidth) / 2
                val rect = RectF(left, y, left + scaledWidth, y + scaledHeight)
                canvas.drawBitmap(logoBitmap, null, rect, paint)
                y += scaledHeight + 12f // Increased gap for better spacing
            }

        // 6. Colors & Sizes
        val colorPrimary = if (isDigital) Color.parseColor("#2E150B") else Color.BLACK
        val colorText = Color.BLACK
        val mainTitleSize = if (is80mm) 12f else 10f
        val subTitleSize  = if (is80mm) 7f  else 6f
        val detailsSize   = if (is80mm) 7f  else 6f  // Smaller font size for bill details
        val bodySize      = if (is80mm) 7f  else 6f  // Match detailsSize for everywhere else

        // 7. Shop Info
        paint.color = colorPrimary
        paint.typeface = boldTypeface
        paint.textSize = mainTitleSize
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
                profile?.shopName?.uppercase() ?: "RESTAURANT",
                (pageWidth / 2).toFloat(),
                y,
                paint
        )

        paint.color = Color.parseColor("#757575") 
        paint.typeface = normalTypeface
        paint.textSize = subTitleSize
        y += 10f
        
        val fullAddress = profile?.shopAddress ?: ""
        if (fullAddress.isNotBlank()) {
            val lines = if (fullAddress.length > 35) {
                val mid = fullAddress.lastIndexOf(",", 35).takeIf { it != -1 } 
                           ?: fullAddress.lastIndexOf(" ", 35).takeIf { it != -1 }
                           ?: 35
                listOf(fullAddress.substring(0, mid + 1).trim(), fullAddress.substring(mid + 1).trim())
            } else {
                listOf(fullAddress)
            }
            
            lines.take(2).forEach { line ->
                if (line.isNotBlank()) {
                    canvas.drawText(line, (pageWidth / 2).toFloat(), y, paint)
                    y += 9f
                }
            }
        }

        paint.color = colorText
        paint.textAlign = Paint.Align.LEFT
        
        if (!profile?.fssaiNumber.isNullOrBlank()) {
            val label = "FSSAI No: "
            val value = profile?.fssaiNumber ?: ""
            paint.typeface = boldTypeface
            val lw = paint.measureText(label)
            paint.typeface = normalTypeface
            val vw = paint.measureText(value)
            val sx = (pageWidth - (lw + vw)) / 2
            paint.typeface = boldTypeface
            canvas.drawText(label, sx, y, paint)
            paint.typeface = normalTypeface
            canvas.drawText(value, sx + lw, y, paint)
            y += 8f
        }

        if (profile?.gstEnabled == true && !profile.gstin.isNullOrBlank()) {
            val label = "GST NO: "
            val value = profile.gstin
            paint.typeface = boldTypeface
            val lw = paint.measureText(label)
            paint.typeface = normalTypeface
            val vw = paint.measureText(value)
            val sx = (pageWidth - (lw + vw)) / 2
            paint.typeface = boldTypeface
            canvas.drawText(label, sx, y, paint)
            paint.typeface = normalTypeface
            canvas.drawText(value, sx + lw, y, paint)
            y += 9f 
        }

        if (!profile?.whatsappNumber.isNullOrBlank()) {
            val label = "Contact: "
            val value = profile?.whatsappNumber ?: ""
            paint.typeface = boldTypeface
            val lw = paint.measureText(label)
            paint.typeface = normalTypeface
            val vw = paint.measureText(value)
            val sx = (pageWidth - (lw + vw)) / 2
            paint.typeface = boldTypeface
            canvas.drawText(label, sx, y, paint)
            paint.typeface = normalTypeface
            canvas.drawText(value, sx + lw, y, paint)
            y += 5f // Reduced gap from 9f to 5f
        }

        // 8. Title with Header Bar
        y += 4f 
        paint.color = colorText
        paint.strokeWidth = 0.5f
        canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)
        
        y += 12f 
        if (isDigital) {
            paint.color = Color.argb(25, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary))
            canvas.drawRect(5f, y - 9f, (pageWidth - 5).toFloat(), y + 3f, paint)
        }
        paint.color = colorPrimary
        paint.typeface = boldTypeface
        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
                if (profile?.gstEnabled == true) "TAX INVOICE" else "INVOICE",
                (pageWidth / 2).toFloat(),
                y,
                paint
        )
        y += 6f 
        paint.color = colorText
        canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

        // 9. Bill Details
        y += 14f
        paint.textSize = detailsSize
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = boldTypeface
        val billNoLabel = "BILL NO: "
        val billNoLabelW = paint.measureText(billNoLabel)  // measured while bold
        canvas.drawText(billNoLabel, 5f, y, paint)
        paint.typeface = normalTypeface
        canvas.drawText("${bill.bill.lifetimeOrderId}", 5f + billNoLabelW, y, paint)
        
        // DATE — measure label with bold font first so position is exact
        val dateStr = com.khanabook.lite.pos.domain.util.DateUtils.formatDateOnly(bill.bill.createdAt)
        paint.typeface = boldTypeface
        val dateLabelWidth = paint.measureText("DATE: ")
        paint.typeface = normalTypeface
        val dateValueWidth = paint.measureText(dateStr)
        val dateBlockRight = (pageWidth - 5).toFloat()
        paint.typeface = boldTypeface
        canvas.drawText("DATE: ", dateBlockRight - dateValueWidth - dateLabelWidth, y, paint)
        paint.typeface = normalTypeface
        canvas.drawText(dateStr, dateBlockRight - dateValueWidth, y, paint)

        y += 10f
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = boldTypeface
        val custLabel = "CUSTOMER: "
        val custLabelW = paint.measureText(custLabel)  // measured while bold
        canvas.drawText(custLabel, 5f, y, paint)
        paint.typeface = normalTypeface
        val rawCust = (bill.bill.customerName ?: "GUEST").uppercase()
        // Prevent customer name from overlapping phone number
        var displayCust = rawCust
        val maxCustW = if (is80mm) 95f else 55f
        if (paint.measureText(displayCust) > maxCustW) {
            while (displayCust.isNotEmpty() && paint.measureText("$displayCust...") > maxCustW) {
                displayCust = displayCust.dropLast(1)
            }
            displayCust += "..."
        }
        canvas.drawText(displayCust, 5f + custLabelW, y, paint)
        
        val rawPhone = bill.bill.customerWhatsapp ?: ""
        if (rawPhone.isNotBlank()) {
            val shouldMask = profile?.maskCustomerPhone ?: true
            val displayPhone = if (shouldMask && rawPhone.length >= 10) {
                rawPhone.take(2) + "XXXXXX" + rawPhone.takeLast(2)
            } else {
                rawPhone
            }
            // PHONE — measure label with bold, value with normal
            paint.typeface = boldTypeface
            val phoneLabelW = paint.measureText("PHONE: ")
            paint.typeface = normalTypeface
            val phoneValueW = paint.measureText(displayPhone)
            val phoneBlockRight = (pageWidth - 5).toFloat()
            paint.typeface = boldTypeface
            canvas.drawText("PHONE: ", phoneBlockRight - phoneValueW - phoneLabelW, y, paint)
            paint.typeface = normalTypeface
            canvas.drawText(displayPhone, phoneBlockRight - phoneValueW, y, paint)
        }

        // 10. Table Header — no HSN column
        // Column right-anchor positions (all numeric cols right-aligned)
        val amtRightX  = (pageWidth - 5).toFloat()
        val qtyRightX  = if (is80mm) 176f else 122f
        val rateRightX = if (is80mm) 141f else 95f

        y += 12f
        paint.strokeWidth = 0.5f
        canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)
        
        y += 10f
        paint.typeface = boldTypeface
        paint.textSize = bodySize
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ITEM", 5f, y, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("RATE", rateRightX, y, paint)
        canvas.drawText("QTY",  qtyRightX,  y, paint)
        canvas.drawText("AMT",  amtRightX,  y, paint)
        
        y += 4f
        paint.strokeWidth = 0.3f
        canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

        // 11. Items
        paint.typeface = normalTypeface
        paint.textSize = bodySize
        y += 12f
        
        val rightMarginFixed = 5f
        // wider item col now that HSN is removed
        val itemColWidth = if (is80mm) 100f else 65f

        bill.items.forEachIndexed { index, item ->
            paint.color = colorText
            paint.textAlign = Paint.Align.LEFT
            val displayName = item.itemName.uppercase()
            
            // Wrapping
            val itemLines = mutableListOf<String>()
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

            val startY = y
            itemLines.forEach { line ->
                canvas.drawText(line, 5f, y, paint)
                y += 9f
            }
            
            val priceY = startY
            // RATE, QTY, AMT – right aligned at their right-anchor positions
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAmount(item.price),     rateRightX, priceY, paint)
            canvas.drawText("${item.quantity}",           qtyRightX,  priceY, paint)
            canvas.drawText(formatAmount(item.itemTotal), amtRightX,  priceY, paint)
            
            y += 2f
            if (index < bill.items.size - 1) {
                paint.color = Color.parseColor("#EEEEEE")
                canvas.drawLine(5f, y, (pageWidth - rightMarginFixed).toFloat(), y, paint)
            }
            y += 10f 
        }

        // 12. Summary — labels LEFT-aligned, values RIGHT-aligned
        y += 2f
        paint.strokeWidth = 0.5f
        paint.color = colorText
        canvas.drawLine(5f, y, (pageWidth - rightMarginFixed).toFloat(), y, paint)
        
        y += 12f
        paint.typeface = normalTypeface
        // Label column starts at ~55% of page; value column right-aligned at edge
        val summaryLabelX = pageWidth * 0.50f
        
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SUB-TOTAL", summaryLabelX, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatAmount(bill.bill.subtotal), amtRightX, y, paint)

        if (profile?.gstEnabled == true) {
            val halfGst = "2.5"
            y += 10f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("CGST ($halfGst%)", summaryLabelX, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAmount(bill.bill.cgstAmount), amtRightX, y, paint)

            y += 10f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("SGST ($halfGst%)", summaryLabelX, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatAmount(bill.bill.sgstAmount), amtRightX, y, paint)
        }

        y += 8f
        paint.strokeWidth = 0.3f
        canvas.drawLine(5f, y, (pageWidth - rightMarginFixed).toFloat(), y, paint)

        // 13. Net Amount Box
        y += 12f
        if (isDigital) {
            paint.color = colorPrimary
            canvas.drawRoundRect(5f, y, (pageWidth - rightMarginFixed).toFloat(), y + 24f, 4f, 4f, paint)
            paint.color = Color.WHITE
        } else {
            paint.color = colorText
            paint.strokeWidth = 0.8f
            canvas.drawLine(5f, y, (pageWidth - rightMarginFixed).toFloat(), y, paint)
            y += 4f
        }
        
        y += 16f
        paint.typeface = boldTypeface
        paint.textSize = bodySize + 2f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("NET AMOUNT", 10f, y, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        val currencySymbol = if (profile?.currency == "INR" || profile?.currency == "Rupee") "Rs." else profile?.currency ?: ""
        canvas.drawText(
                "$currencySymbol ${formatAmount(bill.bill.totalAmount)}",
                (pageWidth - 10).toFloat(),
                y,
                paint
        )

        y += 24f
        paint.color = colorText
        paint.typeface = normalTypeface
        paint.textSize = bodySize
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "PAYMENT MODE: ${com.khanabook.lite.pos.domain.model.PaymentMode.fromDbValue(bill.bill.paymentMode).displayLabel.uppercase()}",
            5f, y, paint
        )

        // 14. QR Codes
        if (profile?.upiHandle?.isNotBlank() == true) {
            y += 20f
            try {
                val amount = try { java.math.BigDecimal(bill.bill.totalAmount).toDouble() } catch (e: Exception) { 0.0 }
                val qrBitmap = QrCodeManager.generateUpiQr(
                    profile.upiHandle ?: "",
                    profile.shopName ?: "RESTAURANT",
                    amount,
                    200
                )
                qrBitmap?.let {
                    val qrSize = if (is80mm) 80f else 60f
                    val left = (pageWidth - qrSize) / 2
                    val rect = RectF(left, y, left + qrSize, y + qrSize)
                    canvas.drawBitmap(it, null, rect, paint)
                    y += qrSize + 10f
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = 6f
                    canvas.drawText("SCAN TO PAY", (pageWidth / 2).toFloat(), y, paint)
                    y += 5f
                    it.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (profile?.reviewUrl?.isNotBlank() == true) {
            y += 20f
            try {
                val reviewQrBitmap = QrCodeManager.generateQr(profile.reviewUrl ?: "", 200)
                reviewQrBitmap?.let {
                    val qrSize = if (is80mm) 80f else 60f
                    val left = (pageWidth - qrSize) / 2
                    val rect = RectF(left, y, left + qrSize, y + qrSize)
                    canvas.drawBitmap(it, null, rect, paint)
                    y += qrSize + 10f
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = 6f
                    canvas.drawText("RATE US / FEEDBACK", (pageWidth / 2).toFloat(), y, paint)
                    y += 5f
                    it.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 15. Footer
        y += 16f 
        paint.typeface = boldTypeface
        paint.textSize = 7f
        paint.textAlign = Paint.Align.CENTER
        if (isDigital) {
            paint.color = colorPrimary
            canvas.drawRect(5f, y - 9f, (pageWidth - 5).toFloat(), y + 4f, paint)
            paint.color = Color.WHITE
        }
        canvas.drawText("THANK YOU! VISIT AGAIN.", (pageWidth / 2).toFloat(), y, paint)

        paint.color = colorText
        paint.typeface = normalTypeface
        y += 14f
        if (profile?.showBranding != false) {
            canvas.drawText("Powered by KhanaBook", (pageWidth / 2).toFloat(), y, paint)
        }
        
        y += 10f
        paint.strokeWidth = 0.5f
        canvas.drawLine(5f, y, (pageWidth - 5).toFloat(), y, paint)

        pdfDocument.finishPage(page)

        val invoiceDir = File(context.cacheDir, "invoices")
        invoiceDir.mkdirs()
        val file = File(invoiceDir, "invoice_${bill.bill.lifetimeOrderId}.pdf")
        try {
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
        } finally {
            pdfDocument.close()
        }
        return file
    } finally {
        logoBitmap?.recycle()
    }
}
}
