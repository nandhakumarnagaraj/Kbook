package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.printing.domain.KitchenTicketFormatter

import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.printing.domain.KotTicketSection
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class KitchenTicketFormatterTest {

    @Test
    fun `kitchen ticket contains order items and notes`() {
        val bill = BillWithItems(
            bill = BillEntity(
                id = 1,
                dailyOrderId = 12,
                dailyOrderDisplay = "2026-04-11-12",
                lifetimeOrderId = 120,
                customerName = "Arun",
                subtotal = "180.0",
                totalAmount = "180.0",
                paymentMode = "cash",
                paymentStatus = "paid",
                orderStatus = "completed"
            ),
            items = listOf(
                BillItemEntity(
                    id = 1,
                    billId = 1,
                    menuItemId = 10,
                    itemName = "Chicken Fried Rice",
                    price = "180.0",
                    quantity = 2,
                    itemTotal = "360.0",
                    specialInstruction = "Less spicy"
                )
            ),
            payments = emptyList()
        )
        val restaurant = RestaurantProfileEntity(shopName = "KhanaBook")
        val printer = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = "00:11:22:33:44:55"
        )

        val output = String(
            KitchenTicketFormatter.format(bill, restaurant, printer),
            Charset.forName("GBK")
        )

        assertTrue(output.contains("Chicken Fried Rice"))
        assertTrue(output.contains("Less spicy"))
        assertTrue(output.contains("2026-04-11-12"))
        // Header keeps order reference, drops shop name and Invoice line
        assertTrue(output.contains("Order: 2026-04-11-12"))
        assertFalse(output.contains("Invoice:"))
        assertFalse(output.contains("KhanaBook"))
    }

    @Test
    fun `event ticket shows type and omits customer and kot fields`() {
        val bill = parcelBill()
        val printer = kitchenPrinter58()
        val output = String(
            KitchenTicketFormatter.formatEventTicket(
                bill = bill,
                restaurantProfile = null,
                printerProfile = printer,
                eventType = KotEventType.ADD,
                itemSnapshotJson = snapshot(
                    quantity = 1,
                    name = "Gobi 65",
                    note = "Extra hot"
                ),
                eventTimeMs = 1780000000000L,
                kotRevision = "4"
            ),
            Charset.forName("GBK")
        )

        assertTrue(output.contains("Order: 2026-09-25-77"))
        assertTrue(output.contains("Type: PARCEL"))
        assertTrue(output.contains("ADDED ITEMS"))
        assertTrue("OUTPUT=[$output]", output.contains("Gobi 65"))
        assertFalse(output.contains("Invoice:"))
        // Customer name and KOT revision are no longer printed on kitchen slips.
        assertFalse(output.contains("KOT #"))
        assertFalse(output.contains("Ravi"))
    }

    @Test
    fun `legacy order type is suppressed from header`() {
        val bill = parcelBill(orderType = "order")
        val output = String(
            KitchenTicketFormatter.formatEventTicket(
                bill = bill,
                restaurantProfile = null,
                printerProfile = kitchenPrinter58(),
                eventType = KotEventType.NEW,
                itemSnapshotJson = snapshot(quantity = 1, name = "Tea"),
                eventTimeMs = 1780000000000L
            ),
            Charset.forName("GBK")
        )

        assertFalse(output.contains("Type:"))
        assertTrue(output.contains("Order: 2026-09-25-77"))
    }

    @Test
    fun `combined ticket renders added and voided sections on one slip`() {
        val bill = parcelBill()
        val output = String(
            KitchenTicketFormatter.formatCombinedTicket(
                bill = bill,
                restaurantProfile = null,
                printerProfile = kitchenPrinter58(),
                sections = listOf(
                    KotTicketSection(
                        eventType = KotEventType.ADD,
                        itemSnapshotJson = snapshot(quantity = 2, name = "Paneer Tikka"),
                        eventTimeMs = 1780000000000L,
                        kotRevision = "5"
                    ),
                    KotTicketSection(
                        eventType = KotEventType.VOID,
                        itemSnapshotJson = snapshot(quantity = 1, name = "Dosa"),
                        eventTimeMs = 1780000005000L,
                        kotRevision = "6"
                    )
                )
            ),
            Charset.forName("GBK")
        )

        assertTrue(output.contains("ADDED ITEMS"))
        assertTrue(output.contains("*** VOIDED ***"))
        // Revision number rides the banner on the same line, not a "KOT #" header.
        assertTrue("OUTPUT=[$output]", output.contains("5 ADDED ITEMS"))
        assertTrue("OUTPUT=[$output]", output.contains("6 *** VOIDED ***"))
        assertTrue("OUTPUT=[$output]", output.contains("2 x Paneer Tikka"))
        assertTrue(output.contains("1 x Dosa"))
        assertFalse(output.contains("KOT #"))
        assertFalse(output.contains("Invoice:"))
        // One header only — Order line appears exactly once
        assertEquals(1, Regex("Order:").findAll(output).count())
    }

    @Test
    fun `cancel ticket shows banner and reason but no items`() {
        val bill = parcelBill()
        val output = String(
            KitchenTicketFormatter.formatCombinedTicket(
                bill = bill,
                restaurantProfile = null,
                printerProfile = kitchenPrinter58(),
                sections = listOf(
                    KotTicketSection(
                        eventType = KotEventType.CANCEL,
                        itemSnapshotJson = snapshot(quantity = 1, name = "Full Meals"),
                        eventTimeMs = 1780000000000L,
                        kotRevision = "9",
                        cancelReason = "Customer left"
                    )
                )
            ),
            Charset.forName("GBK")
        )

        assertTrue(output.contains("*** ORDER CANCELLED ***"))
        assertTrue(output.contains("Reason: Customer left"))
        // A cancelled order must not print its food lines to the kitchen.
        assertFalse("OUTPUT=[$output]", output.contains("Full Meals"))
    }

    @Test
    fun `new ticket carries created banner`() {
        val bill = parcelBill()
        val output = String(
            KitchenTicketFormatter.formatCombinedTicket(
                bill = bill,
                restaurantProfile = null,
                printerProfile = kitchenPrinter58(),
                sections = listOf(
                    KotTicketSection(
                        eventType = KotEventType.NEW,
                        itemSnapshotJson = snapshot(quantity = 1, name = "Idli"),
                        eventTimeMs = 1780000000000L,
                        kotRevision = "1"
                    )
                )
            ),
            Charset.forName("GBK")
        )

        assertTrue("OUTPUT=[$output]", output.contains("1 *** CREATED ***"))
        assertTrue(output.contains("1 x Idli"))
    }

    // ------------------------------------------------------------------

    private fun parcelBill(orderType: String = "parcel"): BillWithItems = BillWithItems(
        bill = BillEntity(
            id = 77,
            dailyOrderId = 77,
            dailyOrderDisplay = "2026-09-25-77",
            lifetimeOrderId = 555,
            orderType = orderType,
            customerName = "Ravi",
            subtotal = "300.0",
            totalAmount = "300.0",
            paymentMode = "cash",
            paymentStatus = "pending",
            orderStatus = "draft"
        ),
        items = emptyList(),
        payments = emptyList()
    )

    private fun kitchenPrinter58() = PrinterProfileEntity(
        role = PrinterRole.KITCHEN.name,
        name = "Kitchen Printer",
        macAddress = "00:11:22:33:44:55",
        paperSize = "58mm"
    )

    /** KOT snapshots are JSON ARRAYS of item objects (see BillRepository.serializeKotItems). */
    private fun snapshot(quantity: Int, name: String, note: String? = null): String {
        val obj = StringBuilder("[{")
            .append("\"id\":1,")
            .append("\"quantity\":").append(quantity).append(",")
            .append("\"itemName\":\"").append(name).append("\"")
        note?.let { obj.append(",\"specialInstruction\":\"").append(it).append("\"") }
        return obj.append("}]").toString()
    }
}