package com.khanabook.lite.pos.feature.onboarding.domain

import android.content.Context
import android.graphics.Bitmap
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
    fun generate(context: Context, signerName: String, signature: Bitmap?, terms: String, version: String): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 48f
        val contentWidth = pageWidth - margin * 2

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val bodyPaint = Paint().apply {
            color = AndroidColor.BLACK
            textSize = 9.5f
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
        fun nextPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }
        fun drawBodyLine(line: String) {
            if (y > pageHeight - margin) nextPage()
            canvas.drawText(line, margin, y, bodyPaint)
            y += bodyPaint.textSize * 1.45f
        }
        canvas.drawText("KHANABOOK RESTAURANT PAYMENT ADDENDUM", margin, y, titlePaint)
        y += 26f
        canvas.drawText("Agreement Version: $version", margin, y, bodyPaint)
        y += 16f
        canvas.drawText("Date: $dateStr", margin, y, bodyPaint)
        y += 22f

        for (paragraph in terms.split("\n")) {
            if (paragraph.isBlank()) {
                y += bodyPaint.textSize * 1.45f
                continue
            }
            var line = ""
            for (word in paragraph.split(" ")) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (bodyPaint.measureText(candidate) > contentWidth && line.isNotEmpty()) {
                    drawBodyLine(line)
                    line = word
                } else {
                    line = candidate
                }
            }
            if (line.isNotEmpty()) drawBodyLine(line)
        }
        y += 24f

        // Keep the signer's identity and drawn signature together on the last page.
        if (y + 170f > pageHeight - margin) nextPage()

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

}
