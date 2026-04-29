package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.model.TopSellingItem
import com.khanabook.lite.pos.domain.util.AppAssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    private fun loadLogoBitmap(profile: RestaurantProfileEntity?): Bitmap? {
        val logoUrl = profile?.logoUrl?.takeIf { it.isNotBlank() }
        if (logoUrl != null) {
            val bitmap = runBlocking(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(logoUrl)
                        .allowHardware(false)
                        .size(160, 160)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    val result = context.imageLoader.execute(request)
                    (result as? SuccessResult)?.drawable?.toBitmap()
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) return bitmap
        }

        return profile?.logoPath?.takeIf { it.isNotBlank() }?.let { path ->
            try { BitmapFactory.decodeFile(AppAssetStore.resolveAssetPath(path)) } catch (_: Exception) { null }
        }
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
        val pBorder    = Paint().apply { strokeWidth = 0.8f; color = Color.rgb(200, 200, 200); style = Paint.Style.STROKE }
        val pHeaderBg  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(50, 50, 50) }
        val pHeaderTxt = Paint().apply { textSize = 11f; isFakeBoldText = true; color = Color.WHITE }
        val pBar       = Paint(Paint.ANTI_ALIAS_FLAG)
        val pCard      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 245, 245) }
        val pCardBorder= Paint().apply { strokeWidth = 1f; color = Color.rgb(210, 210, 210); style = Paint.Style.STROKE }
        val pCardLabel = Paint().apply { textSize = 11f; color = Color.rgb(90, 90, 90) }
        val pCardVal   = Paint().apply { textSize = 22f; isFakeBoldText = true; color = Color.rgb(20, 20, 20) }

        // Draw text centred inside a column
        fun drawCentered(text: String, centerX: Float, textY: Float, paint: Paint) {
            val saved = paint.textAlign
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText(text, centerX, textY, paint)
            paint.textAlign = saved
        }

        fun newPage() {
            document.finishPage(page)
            pageNum++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = margin
        }

        fun overflow(need: Float = 26f) { if (y > pageHeight - margin - need) newPage() }

        // ── SHOP HEADER ────────────────────────────────────────────
        val logoBitmap: Bitmap? = loadLogoBitmap(profile)

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

        // Column boundaries and centers for Payment Summary
        val psC0 = margin
        val psC1 = margin + 110f
        val psC2 = margin + 330f
        val psC3 = margin + 440f
        val psCx0 = (psC0 + psC1) / 2f
        val psCx1 = (psC1 + psC2) / 2f
        val psCx2 = (psC2 + psC3) / 2f
        val psCx3 = (psC3 + contentRight) / 2f

        // Header row with dark background
        val summaryRowH = 22f
        canvas.drawRect(margin, y, contentRight, y + summaryRowH, pHeaderBg)
        drawCentered("Mode",         psCx0, y + 15f, pHeaderTxt)
        drawCentered("Distribution", psCx1, y + 15f, pHeaderTxt)
        drawCentered("Amount (₹)",   psCx2, y + 15f, pHeaderTxt)
        drawCentered("Share",        psCx3, y + 15f, pHeaderTxt)
        y += summaryRowH

        val barMaxW = 190f
        val barH    = 13f
        val barX    = psCx1 - (barMaxW / 2f)

        enabledModes.forEachIndexed { idx, mode ->
            val amtStr = paymentBreakdown[mode.displayLabel] ?: "0.00"
            val amt    = amtStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val frac   = if (grandTotal > BigDecimal.ZERO) (amt.toFloat() / grandTotal.toFloat()).coerceIn(0f, 1f) else 0f
            val barW   = if (frac > 0f) (frac * barMaxW).coerceAtLeast(4f) else 0f
            val pct    = "%.0f%%".format(frac * 100f)
            val rowBg  = if (idx % 2 == 0) Paint().apply { color = Color.rgb(252, 252, 252) } else Paint().apply { color = Color.WHITE }
            canvas.drawRect(margin, y, contentRight, y + 22f, rowBg)

            drawCentered(mode.displayLabel, psCx0, y + 15f, pCell)

            pBar.color = Color.rgb(225, 225, 225)
            canvas.drawRoundRect(RectF(barX, y + 4f, barX + barMaxW, y + 17f), 4f, 4f, pBar)
            if (barW > 0f) {
                pBar.color = modeColor(mode.displayLabel)
                canvas.drawRoundRect(RectF(barX, y + 4f, barX + barW, y + 17f), 4f, 4f, pBar)
            }

            drawCentered("₹$amtStr", psCx2, y + 15f, pBoldCell)
            drawCentered(pct,        psCx3, y + 15f, pCell)
            canvas.drawLine(margin, y + 22f, contentRight, y + 22f, pLine)
            y += 22f
        }

        // Total row
        val totalRowBg = Paint().apply { color = Color.rgb(240, 240, 240) }
        canvas.drawRect(margin, y, contentRight, y + 22f, totalRowBg)
        drawCentered("TOTAL", psCx0, y + 15f, pTotal)
        drawCentered("₹${grandTotal.setScale(2, RoundingMode.HALF_UP)}", psCx2, y + 15f, pTotal)
        canvas.drawRect(margin, y, contentRight, y + 22f, pBorder)
        y += 22f + 10f
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
            canvas.drawRoundRect(RectF(cx, y, cx + cardW, y + cardH), 8f, 8f, pCardBorder)
            canvas.drawText(label, cx + 10f, y + 20f, pCardLabel)
            canvas.drawText(value, cx + 10f, y + 52f, pCardVal)
        }
        y += cardH + 24f
        canvas.drawLine(margin, y, contentRight, y, pThick); y += 26f

        // ── TOP 5 BEST SELLING ITEMS ───────────────────────────────
        if (topItems.isNotEmpty()) {
            canvas.drawText("TOP 5 BEST SELLING ITEMS", margin, y, pSection); y += 18f

            val tsC0 = margin
            val tsC1 = margin + 30f
            val tsC2 = margin + 350f
            val tsC3 = margin + 440f
            val tsCx0 = (tsC0 + tsC1) / 2f
            val tsCx1 = (tsC1 + tsC2) / 2f
            val tsCx2 = (tsC2 + tsC3) / 2f
            val tsCx3 = (tsC3 + contentRight) / 2f

            val topRowH = 20f
            canvas.drawRect(margin, y, contentRight, y + topRowH, pHeaderBg)
            drawCentered("#",            tsCx0, y + 14f, pHeaderTxt)
            drawCentered("Item Name",    tsCx1, y + 14f, pHeaderTxt)
            drawCentered("Qty Sold",     tsCx2, y + 14f, pHeaderTxt)
            drawCentered("Revenue (₹)",  tsCx3, y + 14f, pHeaderTxt)
            y += topRowH

            topItems.forEachIndexed { idx, item ->
                val rowBg = if (idx % 2 == 0) Paint().apply { color = Color.rgb(252, 252, 252) } else Paint().apply { color = Color.WHITE }
                canvas.drawRect(margin, y, contentRight, y + topRowH, rowBg)
                drawCentered("${idx + 1}",            tsCx0, y + 14f, pBoldCell)
                drawCentered(item.itemName.take(50),  tsCx1, y + 14f, pCell)
                drawCentered("${item.quantitySold}",  tsCx2, y + 14f, pCell)
                drawCentered("₹${item.revenue}",      tsCx3, y + 14f, pCell)
                canvas.drawLine(margin, y + topRowH, contentRight, y + topRowH, pLine)
                y += topRowH
            }
            canvas.drawRect(margin, y - topItems.size * topRowH, contentRight, y, pBorder)
            y += 10f
            canvas.drawLine(margin, y, contentRight, y, pThick); y += 20f
        }

        // ── ORDER DETAILS TABLE (always starts on a new page) ─────
        if (orderRows.isNotEmpty()) {
            newPage()

            // Column positions — balanced widths across 515px content area
            //  S.No(28) | Invoice(55) | Date(58) | Mode(88) | Status(65) | Amount(65) | Items(156)
            val c0 = margin            // S.No
            val c1 = margin + 28f      // Invoice
            val c2 = margin + 83f      // Date
            val c3 = margin + 141f     // Mode
            val c4 = margin + 229f     // Status
            val c5 = margin + 294f     // Amount
            val c6 = margin + 359f     // Items & Count
            val itemsColW = contentRight - c6 - 4f   // ~152px

            val hdrH   = 22f
            val lineH  = 14f
            val pItems = Paint().apply { textSize = 9f; color = Color.rgb(60, 60, 60) }
            val pModeSmall = Paint().apply { textSize = 8.5f; color = Color.rgb(35, 35, 35) }

            fun wrapItems(text: String): List<String> {
                if (text == "-") return listOf("-")
                val parts = text.split(", ")
                val lines = mutableListOf<String>()
                var cur = ""
                for (part in parts) {
                    val candidate = if (cur.isEmpty()) part else "$cur, $part"
                    if (pItems.measureText(candidate) <= itemsColW) cur = candidate
                    else { if (cur.isNotEmpty()) lines.add(cur); cur = part }
                }
                if (cur.isNotEmpty()) lines.add(cur)
                return lines.ifEmpty { listOf("-") }
            }

            // Column centre X positions for centred text
            val cx0 = (c0 + c1) / 2f
            val cx1 = (c1 + c2) / 2f
            val cx2 = (c2 + c3) / 2f
            val cx3 = (c3 + c4) / 2f
            val cx4 = (c4 + c5) / 2f
            val cx5 = (c5 + c6) / 2f
            val cx6 = (c6 + contentRight) / 2f

            val divPaint = Paint().apply { strokeWidth = 0.5f }

            fun drawOrderTableHeader() {
                canvas.drawRect(margin, y, contentRight, y + hdrH, pHeaderBg)
                divPaint.color = Color.rgb(80, 80, 80)
                listOf(c1, c2, c3, c4, c5, c6).forEach { cx ->
                    canvas.drawLine(cx, y, cx, y + hdrH, divPaint)
                }
                drawCentered("S.No",           cx0, y + 15f, pHeaderTxt)
                drawCentered("Invoice",         cx1, y + 15f, pHeaderTxt)
                drawCentered("Date",            cx2, y + 15f, pHeaderTxt)
                drawCentered("Mode",            cx3, y + 15f, pHeaderTxt)
                drawCentered("Status",          cx4, y + 15f, pHeaderTxt)
                drawCentered("Amount",          cx5, y + 15f, pHeaderTxt)
                drawCentered("Items and Count", cx6, y + 15f, pHeaderTxt)
                y += hdrH
            }

            canvas.drawText("ORDER DETAILS  (${orderRows.size} orders)", margin, y, pSection)
            y += 20f
            drawOrderTableHeader()

            var sectionStartY = y

            orderRows.forEachIndexed { idx, row ->
                val items = billDataById[row.billId]?.items
                val itemsText = if (items.isNullOrEmpty()) "-" else
                    items.joinToString(", ") {
                        val n = if (it.variantName != null) "${it.itemName} (${it.variantName})" else it.itemName
                        "${it.quantity}x $n"
                    }
                val itemsLines = wrapItems(itemsText)
                val rowH = (itemsLines.size * lineH) + 8f

                // New page — close current section border, redraw header
                if (y > pageHeight - margin - rowH - 10f) {
                    canvas.drawRect(margin, sectionStartY, contentRight, y, pBorder)
                    newPage()
                    canvas.drawText("ORDER DETAILS", margin, y, pSection)
                    y += 20f
                    drawOrderTableHeader()
                    sectionStartY = y
                }

                val status = when (row.orderStatus) {
                    OrderStatus.DRAFT -> "Pending"
                    else -> row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                }

                val rowBg = if (idx % 2 == 0)
                    Paint().apply { color = Color.rgb(250, 250, 250) }
                else
                    Paint().apply { color = Color.WHITE }
                canvas.drawRect(margin, y, contentRight, y + rowH, rowBg)

                divPaint.color = Color.rgb(210, 210, 210)
                listOf(c1, c2, c3, c4, c5, c6).forEach { cx ->
                    canvas.drawLine(cx, y, cx, y + rowH, divPaint)
                }

                val textY = y + lineH

                drawCentered("${idx + 1}",          cx0, textY, pCell)
                drawCentered("INV${row.lifetimeNo}", cx1, textY, pBoldCell)
                drawCentered(fmtDate(row.salesDate), cx2, textY, pCell)

                val modeLabel = row.payMode.displayLabel
                val modePaint = if (modeLabel.length > 8) pModeSmall else pCell
                drawCentered(modeLabel, cx3, textY, modePaint)

                val statusColor = when (row.orderStatus) {
                    OrderStatus.COMPLETED -> Color.rgb(40, 140, 40)
                    OrderStatus.CANCELLED -> Color.rgb(190, 30, 30)
                    else -> Color.GRAY
                }
                val statusPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = statusColor }
                drawCentered(status,               cx4, textY, statusPaint)
                drawCentered("Rs.${row.salesAmount}", cx5, textY, pBoldCell)

                // Items column: left-aligned within column (wrapping), header centred above
                itemsLines.forEachIndexed { i, line ->
                    canvas.drawText(line, c6 + 4f, textY + i * lineH, pItems)
                }

                canvas.drawLine(margin, y + rowH, contentRight, y + rowH, pLine)
                y += rowH
            }
            // Close final section border
            canvas.drawRect(margin, sectionStartY, contentRight, y, pBorder)
            y += 10f
        }

        if (y > pageHeight - margin - 20f) newPage()
        canvas.drawText("Generated by KhanaBook  |  $dateRange", margin, y, pFooter)

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
                val items = bd?.items?.joinToString(" | ") { "${it.quantity}x ${it.itemName}" } ?: "-"
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
