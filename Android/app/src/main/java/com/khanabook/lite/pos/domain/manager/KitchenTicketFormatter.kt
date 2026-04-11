package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.util.DateUtils
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
        printerProfile: PrinterProfileEntity
    ): ByteArray {
        val charsPerLine = if (printerProfile.paperSize == "80mm") 42 else 32
        val line = "-".repeat(charsPerLine)
        val out = mutableListOf<Byte>()

        fun add(bytes: ByteArray) { out.addAll(bytes.toList()) }
        fun add(text: String) { out.addAll(text.toByteArray(Charset.forName("GBK")).toList()) }

        add(RESET)
        add(ALIGN_CENTER)
        add(BOLD_ON)
        add(LARGE_FONT)
        add("KITCHEN TICKET\n")
        add(NORMAL_FONT)
        add(BOLD_OFF)
        add("${restaurantProfile?.shopName ?: "RESTAURANT"}\n")
        add("$line\n")
        add(ALIGN_LEFT)
        add("Order: ${bill.bill.dailyOrderDisplay}\n")
        add("Invoice: INV${bill.bill.lifetimeOrderId}\n")
        add("Time: ${DateUtils.formatDisplay(bill.bill.createdAt)}\n")
        bill.bill.customerName?.takeIf { it.isNotBlank() }?.let { add("Customer: $it\n") }
        add("$line\n")

        bill.items.forEach { item ->
            add(BOLD_ON)
            add("${item.quantity} x ${item.itemName}\n")
            add(BOLD_OFF)
            item.variantName?.takeIf { it.isNotBlank() }?.let { add("  Variant: $it\n") }
            item.specialInstruction?.takeIf { it.isNotBlank() }?.let { add("  Note: $it\n") }
        }

        add("$line\n")
        add(ALIGN_CENTER)
        add("Print Role: ${printerProfile.role}\n")
        add("\n\n\n")
        add(CUT_PAPER)
        return out.toByteArray()
    }
}
