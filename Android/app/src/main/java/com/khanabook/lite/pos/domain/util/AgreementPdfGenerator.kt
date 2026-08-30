package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AgreementPdfGenerator {
    const val AGREEMENT_VERSION = "1.0"

    val TERMS: String = """
        KHANABOOK MERCHANT SERVICES AGREEMENT

        This Merchant Services Agreement ("Agreement") is entered into between KhanaBook (the "Platform") and the restaurant owner identified below ("Merchant").

        1. SERVICES. The Platform provides the Merchant with point-of-sale, billing, inventory, payments, and related business management services through the KhanaBook application and associated web dashboard.

        2. FEES AND SETTLEMENTS. The Merchant agrees to the applicable platform and payment-processing fees disclosed at the time of onboarding. Settlements are made to the Merchant's verified bank account in accordance with the payout schedule.

        3. DATA AND PRIVACY. The Merchant consents to the collection and processing of business and transaction data as required to operate the services, in line with the Platform's privacy policy.

        4. COMPLIANCE. The Merchant shall operate lawfully, maintain valid FSSAI and tax registrations where applicable, and shall not use the Platform for any fraudulent or unlawful activity.

        5. TERM AND TERMINATION. Either party may terminate this Agreement with notice. Upon termination, the Merchant remains responsible for settled and pending obligations.

        6. LIABILITY. The Platform is provided on an "as is" basis. The Platform's liability is limited to the extent permitted by applicable law.

        By signing below, the Merchant acknowledges that they have read, understood, and agree to be bound by the terms of this Agreement.
    """.trimIndent()

    fun generate(context: Context, signerName: String, signature: Bitmap?): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val contentWidth = pageWidth - margin * 2

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val bodyPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val titlePaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val labelPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date())

        var y = margin
        canvas.drawText("KHANABOOK MERCHANT SERVICES AGREEMENT", margin, y, titlePaint)
        y += 26f
        canvas.drawText("Agreement Version: $AGREEMENT_VERSION", margin, y, bodyPaint)
        y += 16f
        canvas.drawText("Date: $dateStr", margin, y, bodyPaint)
        y += 22f

        y = drawWrappedText(canvas, TERMS, margin, y, contentWidth, bodyPaint)
        y += 24f

        canvas.drawText("Signed by: ${signerName.ifBlank { "________________" }}", margin, y, labelPaint)
        y += 18f
        canvas.drawText("Date: $dateStr", margin, y, bodyPaint)
        y += 14f
        canvas.drawText("Signature:", margin, y, labelPaint)
        y += 8f

        if (signature != null) {
            val maxW = 240f
            val scale = maxW / signature.width
            val drawH = (signature.height * scale).coerceAtMost(110f)
            val src = Rect(0, 0, signature.width, signature.height)
            val dst = Rect(
                margin.toInt(),
                y.toInt(),
                (margin + maxW).toInt(),
                (y + drawH).toInt()
            )
            canvas.drawBitmap(signature, src, dst, bodyPaint)
            y += drawH + 8f
        }
        canvas.drawLine(margin, y, margin + 240f, y, bodyPaint)

        pdf.finishPage(page)

        val file = File(context.cacheDir, "merchant_agreement_${System.currentTimeMillis()}.pdf")
        pdf.writeTo(file.outputStream())
        pdf.close()
        return file
    }

    private fun drawWrappedText(
        canvas: AndroidCanvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint
    ): Float {
        var y = startY
        val lineHeight = paint.textSize * 1.45f
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) {
                y += lineHeight
                continue
            }
            val words = paragraph.split(" ")
            var line = ""
            for (word in words) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                    canvas.drawText(line, x, y, paint)
                    y += lineHeight
                    line = word
                } else {
                    line = test
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
            }
        }
        return y
    }
}
