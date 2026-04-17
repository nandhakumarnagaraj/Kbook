package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReportExporter(private val context: Context) {

    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yy").withZone(ZoneId.systemDefault())
    private fun fmtDate(ts: Long) = dateFmt.format(Instant.ofEpochMilli(ts))

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f

    fun exportToPdf(
        reportType: String,
        timeFilter: String,
        paymentBreakdown: Map<String, String>,
        orderRows: List<OrderDetailRow>,
        shopName: String?,
        billDataById: Map<Long, BillWithItems> = emptyMap()
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin

        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true; color = Color.BLACK }
        val subPaint = Paint().apply { textSize = 11f; color = Color.DKGRAY }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = Color.BLACK }
        val cellPaint = Paint().apply { textSize = 10f; color = Color.BLACK }
        val linePaint = Paint().apply { strokeWidth = 0.5f; color = Color.LTGRAY }

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = margin
        }

        fun checkOverflow() { if (y > pageHeight - margin) newPage() }

        shopName?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, margin, y, titlePaint); y += 22f
        }
        canvas.drawText("$reportType Level Report — $timeFilter", margin, y, titlePaint); y += 18f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint); y += 14f

        if (reportType == "Payment") {
            canvas.drawText("Payment Mode", margin, y, headerPaint)
            canvas.drawText("Amount (Rs.)", margin + 300f, y, headerPaint)
            y += 12f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint); y += 12f
            paymentBreakdown.forEach { (mode, amount) ->
                if (!mode.contains("_part")) {
                    checkOverflow()
                    canvas.drawText(mode, margin, y, cellPaint)
                    canvas.drawText(amount, margin + 300f, y, cellPaint)
                    y += 16f
                }
            }
        } else {
            // S.No | Invoice | Date | Customer | Items & Count | Mode | Status | Amount
            val c = listOf(margin, margin + 30f, margin + 85f, margin + 155f, margin + 240f, margin + 400f, margin + 450f, margin + 500f)
            canvas.drawText("S.No", c[0], y, headerPaint)
            canvas.drawText("Invoice", c[1], y, headerPaint)
            canvas.drawText("Date", c[2], y, headerPaint)
            canvas.drawText("Customer", c[3], y, headerPaint)
            canvas.drawText("Items & Count", c[4], y, headerPaint)
            canvas.drawText("Mode", c[5], y, headerPaint)
            canvas.drawText("Status", c[6], y, headerPaint)
            canvas.drawText("Amount", c[7], y, headerPaint)
            y += 12f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint); y += 12f

            orderRows.forEachIndexed { idx, row ->
                checkOverflow()
                val status = row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                val billData = billDataById[row.billId]
                val customer = billData?.bill?.customerWhatsapp?.takeIf { it.isNotBlank() } ?: "—"
                val itemsSummary = billData?.items
                    ?.joinToString(", ") { "${it.itemName} x${it.quantity}" }
                    ?.take(35) ?: "—"
                canvas.drawText("${idx + 1}", c[0], y, cellPaint)
                canvas.drawText("INV${row.lifetimeNo}", c[1], y, cellPaint)
                canvas.drawText(fmtDate(row.salesDate), c[2], y, cellPaint)
                canvas.drawText(customer.take(12), c[3], y, cellPaint)
                canvas.drawText(itemsSummary, c[4], y, cellPaint)
                canvas.drawText(row.payMode.displayLabel.take(8), c[5], y, cellPaint)
                canvas.drawText(status.take(9), c[6], y, cellPaint)
                canvas.drawText(row.salesAmount, c[7], y, cellPaint)
                y += 14f
            }
        }

        y += 10f
        checkOverflow()
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint); y += 12f
        canvas.drawText("Generated by KhanaBook", margin, y, subPaint)

        document.finishPage(page)
        val file = File(context.cacheDir, "KhanaBook_${reportType}_${timeFilter}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun exportToCsv(
        reportType: String,
        timeFilter: String,
        paymentBreakdown: Map<String, String>,
        orderRows: List<OrderDetailRow>,
        shopName: String?,
        billDataById: Map<Long, BillWithItems> = emptyMap()
    ): File {
        val sb = StringBuilder()
        shopName?.takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
        sb.appendLine("$reportType Level Report - $timeFilter")
        sb.appendLine()

        if (reportType == "Payment") {
            sb.appendLine("Payment Mode,Amount (Rs.)")
            paymentBreakdown.forEach { (mode, amount) ->
                if (!mode.contains("_part")) sb.appendLine("$mode,$amount")
            }
        } else {
            sb.appendLine("S.No,Invoice No,Customer Number,Items & Count,Payment Mode,Status,Amount (Rs.)")
            orderRows.forEachIndexed { idx, row ->
                val status = row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                val billData = billDataById[row.billId]
                val customer = billData?.bill?.customerWhatsapp?.takeIf { it.isNotBlank() } ?: "—"
                val itemsSummary = billData?.items
                    ?.joinToString(" | ") { "${it.itemName} x${it.quantity}" } ?: "—"
                sb.appendLine("${idx + 1},INV${row.lifetimeNo},$customer,\"$itemsSummary\",${row.payMode.displayLabel},$status,${row.salesAmount}")
            }
        }

        sb.appendLine()
        sb.appendLine("Generated by KhanaBook")
        val file = File(context.cacheDir, "KhanaBook_${reportType}_${timeFilter}.csv")
        file.writeText(sb.toString())
        return file
    }
}
