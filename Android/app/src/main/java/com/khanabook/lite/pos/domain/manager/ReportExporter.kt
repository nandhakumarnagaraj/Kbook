package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.model.TopSellingItem
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReportExporter(private val context: Context) {

    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yy").withZone(ZoneId.systemDefault())
    private val fileDateFmt = DateTimeFormatter.ofPattern("ddMMMyy").withZone(ZoneId.systemDefault())

    private fun fmtDate(ts: Long) = dateFmt.format(Instant.ofEpochMilli(ts))
    private fun fileDate(ts: Long) = fileDateFmt.format(Instant.ofEpochMilli(ts))

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentRight get() = pageWidth - margin

    private fun modeColor(mode: String): Int = when {
        mode.contains("Cash", ignoreCase = true) -> Color.rgb(67, 160, 71)
        mode.contains("UPI", ignoreCase = true) -> Color.rgb(251, 140, 0)
        mode.contains("POS", ignoreCase = true) -> Color.rgb(142, 36, 170)
        mode.contains("Zomato", ignoreCase = true) -> Color.rgb(198, 40, 40)
        mode.contains("Swiggy", ignoreCase = true) -> Color.rgb(230, 81, 0)
        mode.contains("Website", ignoreCase = true) -> Color.rgb(21, 101, 192)
        else -> Color.rgb(110, 110, 110)
    }

    private fun safeFileName(shopName: String?, timeFilter: String, fromMillis: Long, toMillis: Long): Pair<String, String> {
        val base = shopName?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9 ]+"), "")
            ?.trim()
            ?.replace(" ", "_")
            ?: "KhanaBook"
        val from = if (fromMillis > 0L) fileDate(fromMillis) else timeFilter
        val to = if (toMillis > 0L) fileDate(toMillis) else timeFilter
        return base to "${from}_to_${to}"
    }

    private fun getEnabledSimpleModes(profile: RestaurantProfileEntity?): List<PaymentMode> {
        if (profile == null) return listOf(PaymentMode.CASH)
        return buildList {
            if (profile.cashEnabled) add(PaymentMode.CASH)
            if (profile.upiEnabled) add(PaymentMode.UPI)
            if (profile.posEnabled) add(PaymentMode.POS)
            if (profile.zomatoEnabled) add(PaymentMode.ZOMATO)
            if (profile.swiggyEnabled) add(PaymentMode.SWIGGY)
            if (profile.ownWebsiteEnabled) add(PaymentMode.OWN_WEBSITE)
        }
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (candidate.length <= maxChars) current = candidate
            else { if (current.isNotEmpty()) lines.add(current); current = word }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    fun exportToPdf(
        reportType: String,
        timeFilter: String,
        paymentBreakdown: Map<String, String>,
        orderRows: List<OrderDetailRow>,
        profile: RestaurantProfileEntity?,
        topItems: List<TopSellingItem>,
        billDataById: Map<Long, BillWithItems> = emptyMap(),
        fromMillis: Long = 0L,
        toMillis: Long = 0L
    ): File {
        val document = PdfDocument()
        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin

        val shopName = profile?.shopName

        // ── PAINTS ─────────────────────────────────────────────────
        val pTitle     = Paint().apply { textSize = 24f; isFakeBoldText = true; color = Color.rgb(20, 20, 20) }
        val pShopSub   = Paint().apply { textSize = 12f; color = Color.rgb(65, 65, 65) }
        val pReportLbl = Paint().apply { textSize = 16f; isFakeBoldText = true; color = Color.rgb(40, 40, 40) }
        val pPeriod    = Paint().apply { textSize = 12f; color = Color.DKGRAY }
        val pSection   = Paint().apply { textSize = 13f; isFakeBoldText = true; color = Color.rgb(50, 50, 50) }
        val pHeader    = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.rgb(70, 70, 70) }
        val pCell      = Paint().apply { textSize = 11f; color = Color.rgb(35, 35, 35) }
        val pBoldCell  = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.BLACK }
        val pTotal     = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.BLACK }
        val pFooter    = Paint().apply { textSize = 9f; color = Color.GRAY }
        val pLine      = Paint().apply { strokeWidth = 0.6f; color = Color.LTGRAY }
        val pThick     = Paint().apply { strokeWidth = 1.2f; color = Color.rgb(180, 180, 180) }
        val pBar       = Paint(Paint.ANTI_ALIAS_FLAG)
        val pCard      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 245, 245) }
        val pCardLabel = Paint().apply { textSize = 11f; color = Color.rgb(90, 90, 90) }
        val pCardVal   = Paint().apply { textSize = 22f; isFakeBoldText = true; color = Color.rgb(20, 20, 20) }

        fun newPage() {
            document.finishPage(page)
            pageNum++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = margin
        }

        fun overflow(need: Float = 26f) { if (y > pageHeight - margin - need) newPage() }

        // ── SHOP HEADER ────────────────────────────────────────────
        val logoBitmap: Bitmap? = profile?.logoPath?.takeIf { it.isNotBlank() }?.let {
            try { BitmapFactory.decodeFile(it) } catch (e: Exception) { null }
        }

        val logoSize = 80f
        val hasLogo = logoBitmap != null
        val textStartX = if (hasLogo) margin + logoSize + 16f else margin
        val logoTopY = y - 14f

        if (hasLogo) {
            val scaled = Bitmap.createScaledBitmap(logoBitmap!!, logoSize.toInt(), logoSize.toInt(), true)
            canvas.drawBitmap(scaled, margin, logoTopY, null)
        }

        var textY = y
        profile?.shopName?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, textStartX, textY, pTitle); textY += 26f
        }
        profile?.shopAddress?.takeIf { it.isNotBlank() }?.let { addr ->
            val addrLines = addr.split("\n").flatMap { wrapText(it, 55) }.take(2)
            addrLines.forEach { line -> canvas.drawText(line, textStartX, textY, pShopSub); textY += 15f }
        }
        profile?.whatsappNumber?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("Phone: $it", textStartX, textY, pShopSub); textY += 15f
        }
        profile?.email?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("Email: $it", textStartX, textY, pShopSub); textY += 15f
        }

        y = maxOf(textY, if (hasLogo) logoTopY + logoSize + 6f else textY)

        profile?.fssaiNumber?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText("FSSAI No: $it", margin, y, pShopSub); y += 16f
        }
        if (profile?.gstEnabled == true) {
            profile.gstin?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText("GSTIN: $it", margin, y, pShopSub); y += 16f
            }
        }

        y += 10f
        canvas.drawLine(margin, y, contentRight, y, pThick); y += 20f

        // ── REPORT INFO ────────────────────────────────────────────
        val reportLabel = if (reportType == "Payment") "Payment Level Report" else "Order Level Report"
        val dateRange = if (fromMillis > 0L && toMillis > 0L)
            "${fmtDate(fromMillis)}  –  ${fmtDate(toMillis)}"
        else timeFilter
        canvas.drawText(reportLabel, margin, y, pReportLbl); y += 22f
        canvas.drawText("Period: $dateRange", margin, y, pPeriod); y += 16f
        canvas.drawLine(margin, y, contentRight, y, pThick); y += 24f

        // ── PAYMENT SUMMARY ────────────────────────────────────────
        val enabledModes = getEnabledSimpleModes(profile)
        val grandTotal = enabledModes.fold(BigDecimal.ZERO) { acc, mode ->
            acc.add(paymentBreakdown[mode.displayLabel]?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }

        canvas.drawText("PAYMENT SUMMARY", margin, y, pSection); y += 18f
        canvas.drawLine(margin, y, contentRight, y, pLine); y += 16f

        canvas.drawText("Mode",            margin,        y, pHeader)
        canvas.drawText("Distribution",    margin + 130f, y, pHeader)
        canvas.drawText("Amount (₹)",      margin + 340f, y, pHeader)
        canvas.drawText("Share",           margin + 460f, y, pHeader)
        y += 14f

        val barMaxW = 190f
        val barH    = 13f
        val barX    = margin + 130f

        enabledModes.forEach { mode ->
            val amtStr = paymentBreakdown[mode.displayLabel] ?: "0.00"
            val amt    = amtStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val frac   = if (grandTotal > BigDecimal.ZERO) (amt.toFloat() / grandTotal.toFloat()).coerceIn(0f, 1f) else 0f
            val barW   = if (frac > 0f) (frac * barMaxW).coerceAtLeast(4f) else 0f
            val pct    = "%.0f%%".format(frac * 100f)

            canvas.drawText(mode.displayLabel, margin, y, pCell)

            pBar.color = Color.rgb(225, 225, 225)
            canvas.drawRoundRect(RectF(barX, y - barH, barX + barMaxW, y), 4f, 4f, pBar)
            if (barW > 0f) {
                pBar.color = modeColor(mode.displayLabel)
                canvas.drawRoundRect(RectF(barX, y - barH, barX + barW, y), 4f, 4f, pBar)
            }

            canvas.drawText("₹$amtStr", margin + 340f, y, pBoldCell)
            canvas.drawText(pct,        margin + 460f, y, pCell)
            y += 25f
        }

        canvas.drawLine(margin + 330f, y, contentRight, y, pLine); y += 10f
        canvas.drawText("TOTAL", margin, y, pTotal)
        canvas.drawText("₹${grandTotal.setScale(2, RoundingMode.HALF_UP)}", margin + 340f, y, pTotal)
        y += 26f
        canvas.drawLine(margin, y, contentRight, y, pThick); y += 26f

        // ── KEY METRICS (card boxes) ───────────────────────────────
        val completedRows = orderRows.filter { it.orderStatus == OrderStatus.COMPLETED }
        val cancelledCount = orderRows.count { it.orderStatus == OrderStatus.CANCELLED }
        val totalRevenue = completedRows.fold(BigDecimal.ZERO) { acc, row ->
            acc.add(row.salesAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
        val avgRevenue = if (completedRows.isNotEmpty())
            totalRevenue.divide(BigDecimal(completedRows.size), 2, RoundingMode.HALF_UP)
        else BigDecimal.ZERO

        canvas.drawText("KEY METRICS", margin, y, pSection); y += 18f
        canvas.drawLine(margin, y, contentRight, y, pLine); y += 16f

        val cardGap  = 10f
        val cardW    = (contentRight - margin - cardGap * 2f) / 3f
        val cardH    = 66f
        val cardData = listOf(
            "Avg Revenue / Order" to "₹$avgRevenue",
            "Completed Orders"   to "${completedRows.size}",
            "Cancelled Orders"   to "$cancelledCount"
        )
        cardData.forEachIndexed { i, (label, value) ->
            val cx = margin + i * (cardW + cardGap)
            canvas.drawRoundRect(RectF(cx, y, cx + cardW, y + cardH), 8f, 8f, pCard)
            canvas.drawText(label, cx + 10f, y + 20f, pCardLabel)
            canvas.drawText(value, cx + 10f, y + 52f, pCardVal)
        }
        y += cardH + 24f
        canvas.drawLine(margin, y, contentRight, y, pThick); y += 26f

        // ── TOP 5 BEST SELLING ITEMS ───────────────────────────────
        if (topItems.isNotEmpty()) {
            canvas.drawText("TOP 5 BEST SELLING ITEMS", margin, y, pSection); y += 18f
            canvas.drawLine(margin, y, contentRight, y, pLine); y += 14f

            canvas.drawText("#",            margin,        y, pHeader)
            canvas.drawText("Item Name",    margin + 24f,  y, pHeader)
            canvas.drawText("Qty Sold",     margin + 350f, y, pHeader)
            canvas.drawText("Revenue (₹)",  margin + 440f, y, pHeader)
            y += 12f
            canvas.drawLine(margin, y, contentRight, y, pLine); y += 12f

            val stripPaint = Paint().apply { color = Color.argb(12, 0, 0, 0) }
            topItems.forEachIndexed { idx, item ->
                if (idx % 2 == 0) canvas.drawRect(margin, y - 12f, contentRight, y + 8f, stripPaint)
                canvas.drawText("${idx + 1}",            margin,        y, pBoldCell)
                canvas.drawText(item.itemName.take(50),  margin + 24f,  y, pCell)
                canvas.drawText("${item.quantitySold}",  margin + 350f, y, pCell)
                canvas.drawText("₹${item.revenue}",      margin + 440f, y, pCell)
                y += 20f
            }
            y += 10f
            canvas.drawLine(margin, y, contentRight, y, pThick); y += 20f
        }

        // ── ORDER DETAILS TABLE (always starts on a new page) ─────
        if (orderRows.isNotEmpty()) {
            newPage()
            canvas.drawText("ORDER DETAILS  (${orderRows.size} orders)", margin, y, pSection); y += 18f
            canvas.drawLine(margin, y, contentRight, y, pLine); y += 14f

            // Column positions sized for 11f font
            val c0 = margin           // #
            val c1 = margin + 16f     // Invoice
            val c2 = margin + 84f     // Date      (68px)
            val c3 = margin + 146f    // Mode      (62px)
            val c4 = margin + 232f    // Status    (86px)
            val c5 = margin + 304f    // Amount    (72px)
            val c6 = margin + 370f    // Items     (66px)
            val itemsColW = contentRight - c6   // ~185px

            val pItems = Paint().apply { textSize = 9f; color = Color.rgb(75, 75, 75) }
            val stripPaint = Paint().apply { color = Color.argb(12, 0, 0, 0) }

            fun wrapItems(text: String): List<String> {
                if (text == "—") return listOf("—")
                val parts = text.split(", ")
                val lines = mutableListOf<String>()
                var cur = ""
                for (part in parts) {
                    val candidate = if (cur.isEmpty()) part else "$cur, $part"
                    if (pItems.measureText(candidate) <= itemsColW) cur = candidate
                    else { if (cur.isNotEmpty()) lines.add(cur); cur = part }
                }
                if (cur.isNotEmpty()) lines.add(cur)
                return lines.ifEmpty { listOf("—") }
            }

            canvas.drawText("#",             c0, y, pHeader)
            canvas.drawText("Invoice",       c1, y, pHeader)
            canvas.drawText("Date",          c2, y, pHeader)
            canvas.drawText("Mode",          c3, y, pHeader)
            canvas.drawText("Status",        c4, y, pHeader)
            canvas.drawText("Amount (₹)",    c5, y, pHeader)
            canvas.drawText("Items & Count", c6, y, pHeader)
            y += 13f
            canvas.drawLine(margin, y, contentRight, y, pLine); y += 13f

            val lineH = 14f

            orderRows.forEachIndexed { idx, row ->
                val items = billDataById[row.billId]?.items
                val itemsText = if (items.isNullOrEmpty()) "—" else
                    items.joinToString(", ") {
                        val name = if (it.variantName != null) "${it.itemName} (${it.variantName})" else it.itemName
                        "$name ×${it.quantity}"
                    }
                val itemsLines = wrapItems(itemsText)
                val rowH = (itemsLines.size * lineH) + 4f

                overflow(rowH + 6f)

                val status = when (row.orderStatus) {
                    OrderStatus.DRAFT -> "Pending"
                    else -> row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                }

                if (idx % 2 == 0) canvas.drawRect(margin, y - 10f, contentRight, y + rowH - 10f, stripPaint)

                canvas.drawText("${idx + 1}", c0, y, pCell)
                canvas.drawText("INV${row.lifetimeNo}", c1, y, pCell)
                canvas.drawText(fmtDate(row.salesDate), c2, y, pCell)
                canvas.drawText(row.payMode.displayLabel.take(14), c3, y, pCell)
                canvas.drawText(status.take(9), c4, y, pCell)
                canvas.drawText("₹${row.salesAmount}", c5, y, pCell)

                itemsLines.forEachIndexed { i, line ->
                    canvas.drawText(line, c6, y + i * lineH, pItems)
                }

                y += rowH + 2f
            }
            y += 6f
        }

        overflow()
        canvas.drawText("Generated by KhanaBook  •  $dateRange", margin, y, pFooter)

        document.finishPage(page)

        val (base, dateTag) = safeFileName(shopName, timeFilter, fromMillis, toMillis)
        val file = File(context.cacheDir, "${base}_Report_${dateTag}.pdf")
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
        billDataById: Map<Long, BillWithItems> = emptyMap(),
        fromMillis: Long = 0L,
        toMillis: Long = 0L
    ): File {
        val dateRange = if (fromMillis > 0L && toMillis > 0L)
            "${fmtDate(fromMillis)} – ${fmtDate(toMillis)}"
        else timeFilter

        val sb = StringBuilder()
        shopName?.takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
        sb.appendLine("$reportType Level Report — $dateRange")
        sb.appendLine()

        val summary = paymentBreakdown.filter { !it.key.contains("_part") && !it.key.contains("+") }
        if (summary.isNotEmpty()) {
            sb.appendLine("PAYMENT SUMMARY")
            sb.appendLine("Payment Mode,Amount (Rs.)")
            summary.entries.sortedByDescending { it.value.toBigDecimalOrNull() ?: BigDecimal.ZERO }
                .forEach { (mode, amt) -> sb.appendLine("$mode,$amt") }
            val total = summary.values.mapNotNull { it.toBigDecimalOrNull() }
                .fold(BigDecimal.ZERO, BigDecimal::add)
            sb.appendLine("TOTAL,${total.setScale(2, RoundingMode.HALF_UP)}")
            sb.appendLine()
        }

        if (reportType != "Payment") {
            sb.appendLine("ORDER DETAILS")
            sb.appendLine("S.No,Invoice No,Date,Payment Mode,Status,Amount (Rs.),Customer,Items")
            orderRows.forEachIndexed { idx, row ->
                val status = row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                val bd = billDataById[row.billId]
                val customer = bd?.bill?.customerWhatsapp?.takeIf { it.isNotBlank() } ?: "—"
                val items = bd?.items?.joinToString(" | ") { "${it.itemName} x${it.quantity}" } ?: "—"
                sb.appendLine("${idx + 1},INV${row.lifetimeNo},${fmtDate(row.salesDate)},${row.payMode.displayLabel},$status,${row.salesAmount},$customer,\"$items\"")
            }
        }

        sb.appendLine()
        sb.appendLine("Generated by KhanaBook")

        val (base, dateTag) = safeFileName(shopName, timeFilter, fromMillis, toMillis)
        val file = File(context.cacheDir, "${base}_Report_${dateTag}.csv")
        file.writeText(sb.toString())
        return file
    }
}
