package com.khanabook.lite.pos.feature.printing.domain

import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.billing.data.getInvoiceNumberDisplay
import com.khanabook.lite.pos.core.util.DateUtils
import java.nio.charset.Charset

object KitchenTicketFormatter {
    private val ESC: Byte = 0x1B
    private val GS: Byte = 0x1D
    private val RESET = byteArrayOf(ESC, 0x40)
    private val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)
    private val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    private val LARGE_FONT = byteArrayOf(GS, 0x21, 0x11)
    private val NORMAL_FONT = byteArrayOf(GS, 0x21, 0x00)
    private val CUT_PAPER = byteArrayOf(GS, 0x56, 0x42, 0x00)

    fun format(
        bill: BillWithItems,
        restaurantProfile: RestaurantProfileEntity?,
        printerProfile: PrinterProfileEntity,
        itemsToPrint: List<com.khanabook.lite.pos.feature.billing.data.BillItemEntity> = bill.items
    ): ByteArray {
        val is80mm = printerProfile.paperSize == "80mm"
        val charsPerLine = if (is80mm) 40 else 32
        val leftPad = if (is80mm) "    " else ""
        val line = leftPad + "-".repeat(charsPerLine)
        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(RESET)
        addBillHeader(out, leftPad, line, bill, restaurantProfile)

        itemsToPrint.forEach { item ->
            add(BOLD_ON)
            add(leftPad + "${item.quantity} x ${item.itemName}\n")
            add(BOLD_OFF)
            item.variantName?.takeIf { it.isNotBlank() }?.let { add(leftPad + "  Variant: $it\n") }
            item.specialInstruction?.takeIf { it.isNotBlank() }?.let { add(leftPad + "  Note: $it\n") }
        }

        add("$line\n")
        add("\n\n\n")
        add(CUT_PAPER)
        return out.toByteArray()
    }

    /**
     * Renders a kitchen ticket from an immutable KOT EVENT snapshot, not the
     * bill's current state. Every kitchen-facing revision (NEW, ADD, VOID,
     * REPRINT, CANCEL) is printed exactly as it was recorded, so late
     * dispatch/retries can never silently leak items added AFTER the event.
     *
     * Banner mapping:
     *  - NEW          → (no banner — the first order ticket)
     *  - ADD          → "ADDED ITEMS" (only the delta rows)
     *  - VOID         → "*** VOIDED ***" (only the voided rows)
     *  - REPRINT      → "*** REPRINT ***" (full order copy)
     *  - CANCEL       → "*** ORDER CANCELLED ***" (full order copy + reason)
     */
    fun formatEventTicket(
        bill: BillWithItems,
        restaurantProfile: RestaurantProfileEntity?,
        printerProfile: PrinterProfileEntity,
        eventType: String,
        itemSnapshotJson: String,
        eventTimeMs: Long,
        cancelReason: String? = null
    ): ByteArray {
        val is80mm = printerProfile.paperSize == "80mm"
        val charsPerLine = if (is80mm) 40 else 32
        val leftPad = if (is80mm) "    " else ""
        val line = leftPad + "-".repeat(charsPerLine)
        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(RESET)
        addBillHeader(out, leftPad, line, bill, restaurantProfile, eventTimeMs)

        val banner = when (eventType) {
            KotEventType.ADD -> "ADDED ITEMS"
            KotEventType.VOID -> "*** VOIDED ***"
            KotEventType.REPRINT -> "*** REPRINT ***"
            KotEventType.CANCEL -> "*** ORDER CANCELLED ***"
            else -> null // NEW: plain first ticket, unchanged layout
        }
        if (banner != null) {
            add(ALIGN_CENTER)
            add(BOLD_ON)
            add(leftPad + "$banner\n")
            add(BOLD_OFF)
            add(ALIGN_LEFT)
        }
        cancelReason?.takeIf { it.isNotBlank() }?.let {
            add(leftPad + "Reason: $it\n")
        }
        add("$line\n")

        for (lineText in parseSnapshotItems(itemSnapshotJson)) {
            add(leftPad + lineText + "\n")
        }

        add("$line\n")
        add("\n\n\n")
        add(CUT_PAPER)
        return out.toByteArray()
    }

    /**
     * Renders a VOID-only kitchen ticket from the item snapshot captured in the KOT event.
     * A full bill is NOT reprinted — only the voided quantities are flagged for the kitchen
     * so staff know what was scrubbed from the order.
     */
    fun formatVoidTicket(
        bill: BillWithItems,
        restaurantProfile: RestaurantProfileEntity?,
        printerProfile: PrinterProfileEntity,
        itemSnapshotJson: String
    ): ByteArray =
        formatEventTicket(
            bill = bill,
            restaurantProfile = restaurantProfile,
            printerProfile = printerProfile,
            eventType = KotEventType.VOID,
            itemSnapshotJson = itemSnapshotJson,
            eventTimeMs = bill.bill.createdAt
        )

    private fun addBillHeader(
        out: MutableList<Byte>,
        leftPad: String,
        line: String,
        bill: BillWithItems,
        restaurantProfile: RestaurantProfileEntity?,
        eventTimeMs: Long = bill.bill.createdAt
    ) {
        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(ALIGN_CENTER)
        add("${restaurantProfile?.shopName ?: "RESTAURANT"}\n")
        add("$line\n")
        add(ALIGN_LEFT)
        add(leftPad + "Order: ${bill.bill.dailyOrderDisplay}\n")
        add(leftPad + "Invoice: ${bill.bill.getInvoiceNumberDisplay()}\n")
        add(leftPad + "Time: ${DateUtils.formatDisplay(eventTimeMs)}\n")
        bill.bill.customerName?.takeIf { it.isNotBlank() }?.let { add(leftPad + "Customer: $it\n") }
        add("$line\n")
    }

    private fun parseSnapshotItems(itemSnapshotJson: String): List<String> {
        if (itemSnapshotJson.isBlank()) return listOf("(items not captured)")
        return try {
            val root = org.json.JSONArray(itemSnapshotJson)
            val lines = mutableListOf<String>()
            for (i in 0 until root.length()) {
                val obj = root.optJSONObject(i) ?: continue
                val qty = obj.optInt("quantity", 1)
                val name = obj.optString("itemName").ifBlank { "Item" }
                val variant = obj.optString("variantName").takeIf { it.isNotBlank() }
                val note = obj.optString("specialInstruction").takeIf { it.isNotBlank() }
                lines += "$qty x $name"
                variant?.let { lines += "  Variant: $it" }
                note?.let { lines += "  Note: $it" }
            }
            if (lines.isEmpty()) listOf("(items not captured)") else lines
        } catch (e: Exception) {
            listOf("(items not captured)")
        }
    }
}
