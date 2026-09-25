package com.khanabook.lite.pos.feature.printing.domain

import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.core.util.DateUtils
import java.nio.charset.Charset

/**
 * One rendered block of a combined kitchen ticket — see
 * [KitchenTicketFormatter.formatCombinedTicket]. Produced when one user save
 * generated several KOT events (batched ADD + VOID sharing an eventToken).
 */
data class KotTicketSection(
    val eventType: String,
    val itemSnapshotJson: String,
    val eventTimeMs: Long,
    val kotRevision: String? = null,
    val cancelReason: String? = null
)

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
        itemsToPrint: List<com.khanabook.lite.pos.feature.billing.data.BillItemEntity> = bill.items,
        kotRevision: String? = null
    ): ByteArray {
        val is80mm = printerProfile.paperSize == "80mm"
        val charsPerLine = if (is80mm) 40 else 32
        val leftPad = if (is80mm) "    " else ""
        val line = leftPad + "-".repeat(charsPerLine)
        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(RESET)
        addBillHeader(out, leftPad, line, bill, restaurantProfile, kotRevision = kotRevision)

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
        cancelReason: String? = null,
        kotRevision: String? = null
    ): ByteArray {
        val is80mm = printerProfile.paperSize == "80mm"
        val charsPerLine = if (is80mm) 40 else 32
        val leftPad = if (is80mm) "    " else ""
        val line = leftPad + "-".repeat(charsPerLine)
        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(RESET)
        addBillHeader(out, leftPad, line, bill, restaurantProfile, eventTimeMs, kotRevision)

        val banner = bannerFor(eventType)
        if (banner != null) {
            emitBanner(out, leftPad, banner)
        }
        cancelReason?.takeIf { it.isNotBlank() }?.let {
            add(leftPad + "Reason: $it\n")
        }
        add("$line\n")

        for (lineText in parseSnapshotItems(itemSnapshotJson)) {
            add(BOLD_ON)
            add(leftPad + lineText + "\n")
            add(BOLD_OFF)
        }

        add("$line\n")
        add("\n\n\n")
        add(CUT_PAPER)
        return out.toByteArray()
    }

    private fun bannerFor(eventType: String): String? = when (eventType) {
        KotEventType.ADD -> "ADDED ITEMS"
        KotEventType.VOID -> "*** VOIDED ***"
        KotEventType.REPRINT -> "*** REPRINT ***"
        KotEventType.CANCEL -> "*** ORDER CANCELLED ***"
        else -> null // NEW: plain first ticket, unchanged layout
    }

    /**
     * Centered bold banner. Banners that fit the narrow double-width line
     * (≤16 chars on 58mm) print in LARGE_FONT so the cook can read them across
     * the kitchen; the long CANCEL banner stays normal size to avoid wrapping.
     */
    private fun emitBanner(out: MutableList<Byte>, leftPad: String, banner: String) {
        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        val useLargeFont = banner.length <= 16
        add(ALIGN_CENTER)
        add(BOLD_ON)
        if (useLargeFont) add(LARGE_FONT)
        add((if (useLargeFont) "" else leftPad) + "$banner\n")
        if (useLargeFont) add(NORMAL_FONT)
        add(BOLD_OFF)
        add(ALIGN_LEFT)
    }

    /**
     * Renders ONE kitchen ticket covering several KOT events from the same user save
     * (batched ADD + VOID). The kitchen gets a single slip showing what was added and
     * what was removed — instead of an immediate ADD ticket followed by a VOID ticket
     * up to 30s later on the queue flush.
     *
     * Sections render in the order given (revision order), each with its own banner
     * and item snapshot. Header time = the most recent change in the batch.
     */
    fun formatCombinedTicket(
        bill: BillWithItems,
        restaurantProfile: RestaurantProfileEntity?,
        printerProfile: PrinterProfileEntity,
        sections: List<KotTicketSection>
    ): ByteArray {
        require(sections.isNotEmpty()) { "Combined ticket needs at least one section" }
        val is80mm = printerProfile.paperSize == "80mm"
        val charsPerLine = if (is80mm) 40 else 32
        val leftPad = if (is80mm) "    " else ""
        val line = leftPad + "-".repeat(charsPerLine)
        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(RESET)
        val headerTime = sections.maxOf { it.eventTimeMs }
        addBillHeader(out, leftPad, line, bill, restaurantProfile, headerTime, sections.last().kotRevision)

        sections.forEach { section ->
            val banner = bannerFor(section.eventType)
            if (banner != null) {
                emitBanner(out, leftPad, banner)
            }
            section.cancelReason?.takeIf { it.isNotBlank() }?.let { add(leftPad + "Reason: $it\n") }
            add("$line\n")
            for (lineText in parseSnapshotItems(section.itemSnapshotJson)) {
                add(BOLD_ON)
                add(leftPad + lineText + "\n")
                add(BOLD_OFF)
            }
            add("\n")
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
        itemSnapshotJson: String,
        kotRevision: String? = null
    ): ByteArray =
        formatEventTicket(
            bill = bill,
            restaurantProfile = restaurantProfile,
            printerProfile = printerProfile,
            eventType = KotEventType.VOID,
            itemSnapshotJson = itemSnapshotJson,
            eventTimeMs = bill.bill.createdAt,
            kotRevision = kotRevision
        )

    /**
     * Kitchen-slip header — intentionally minimal. The kitchen needs to match the
     * ticket to an order and know WHEN it changed, nothing else:
     *   Order: {dailyOrderDisplay}   — primary reference
     *   Type: {orderType}            — only when meaningful (legacy "order" suppressed)
     *   Time: {event time}           — when this revision happened
     *   Customer: {name}             — only when present (takeaway callouts)
     *   KOT #{revision}              — helps spot missing/late tickets
     *
     * Deliberately removed: shop name (branding lives on the customer invoice) and
     * the Invoice line — for drafts getInvoiceNumberDisplay() falls back to the
     * dailyOrderDisplay string, printing the same value twice on one slip.
     */
    private fun addBillHeader(
        out: MutableList<Byte>,
        leftPad: String,
        line: String,
        bill: BillWithItems,
        @Suppress("UNUSED_PARAMETER") restaurantProfile: RestaurantProfileEntity?,
        eventTimeMs: Long = bill.bill.createdAt,
        kotRevision: String? = null
    ) {
        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(ALIGN_LEFT)
        add(leftPad + "Order: ${bill.bill.dailyOrderDisplay}\n")
        val orderType = bill.bill.orderType.trim()
        if (orderType.isNotEmpty() && !orderType.equals("order", ignoreCase = true)) {
            add(leftPad + "Type: ${orderType.uppercase()}\n")
        }
        add(leftPad + "Time: ${DateUtils.formatDisplay(eventTimeMs)}\n")
        bill.bill.customerName?.takeIf { it.isNotBlank() }?.let { add(leftPad + "Customer: $it\n") }
        kotRevision?.takeIf { it.isNotBlank() }?.let { add(leftPad + "KOT #$it\n") }
        add("$line\n")
    }

    /**
     * Parses the event's item snapshot into printable lines. Uses Gson (not org.json)
     * so JVM unit tests exercise the real parser — org.json is stubbed in android.jar.
     */
    private fun parseSnapshotItems(itemSnapshotJson: String): List<String> {
        if (itemSnapshotJson.isBlank()) return listOf("(items not captured)")
        return try {
            val root = com.google.gson.JsonParser.parseString(itemSnapshotJson).asJsonArray
            val lines = mutableListOf<String>()
            for (element in root) {
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                fun string(key: String): String? =
                    obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                val qty = obj.get("quantity")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1
                val name = string("itemName") ?: "Item"
                lines += "$qty x $name"
                string("variantName")?.let { lines += "  Variant: $it" }
                string("specialInstruction")?.let { lines += "  Note: $it" }
            }
            if (lines.isEmpty()) listOf("(items not captured)") else lines
        } catch (e: Exception) {
            listOf("(items not captured)")
        }
    }
}
