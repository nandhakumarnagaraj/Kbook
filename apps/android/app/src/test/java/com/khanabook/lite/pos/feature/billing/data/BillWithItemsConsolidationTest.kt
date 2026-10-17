package com.khanabook.lite.pos.feature.billing.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * BillingViewModel.appendItemsToDraft stores a quantity INCREASE as a brand-new
 * bill_items row carrying only the delta (it never updates the original row), so a
 * bill can legitimately hold several rows for the same menuItemId + variantId.
 *
 * BillWithItems.getConsolidatedItems() is what every consumer (GST invoice, PDF,
 * print and the active-order table) must use so those rows collapse into one line.
 */
class BillWithItemsConsolidationTest {

    private fun billWith(items: List<BillItemEntity>) = BillWithItems(
        bill = BillEntity(
            id = 1,
            dailyOrderId = 1L,
            dailyOrderDisplay = "ORD-001",
            lifetimeOrderId = 1L,
            subtotal = "1000.00",
            totalAmount = "1000.00",
            paymentMode = "cash",
            paymentStatus = "pending",
            orderStatus = "active"
        ),
        items = items,
        payments = emptyList()
    )

    private fun item(
        id: Long,
        menuItemId: Long,
        name: String,
        qty: Int,
        price: String,
        variantId: Long? = null,
        sentToKot: Boolean = false
    ) = BillItemEntity(
        id = id,
        billId = 1,
        menuItemId = menuItemId,
        variantId = variantId,
        itemName = name,
        price = price,
        quantity = qty,
        itemTotal = BigDecimal(price).multiply(BigDecimal.valueOf(qty.toLong())).toPlainString(),
        sentToKot = sentToKot
    )

    @Test
    fun `merges the delta row into the original row for the same item and variant`() {
        // Row 11 is the original line; row 57 is the +1 delta row that
        // appendItemsToDraft inserted when the guest bumped the count from 2 to 3.
        val bill = billWith(
            listOf(
                item(11, menuItemId = 5, name = "Paneer Butter Masala", qty = 2, price = "210.00", sentToKot = true),
                item(57, menuItemId = 5, name = "Paneer Butter Masala", qty = 1, price = "210.00")
            )
        )

        val consolidated = bill.getConsolidatedItems()

        assertEquals(1, consolidated.size)
        assertEquals(3, consolidated[0].quantity)
        assertEquals("630.00", consolidated[0].itemTotal)
    }

    @Test
    fun `keeps the bumped item in its original position instead of appending it last`() {
        val bill = billWith(
            listOf(
                item(11, menuItemId = 5, name = "Paneer Butter Masala", qty = 2, price = "210.00"),
                item(12, menuItemId = 6, name = "Garlic Naan", qty = 1, price = "50.00"),
                item(57, menuItemId = 5, name = "Paneer Butter Masala", qty = 1, price = "210.00")
            )
        )

        val consolidated = bill.getConsolidatedItems()

        assertEquals(listOf("Paneer Butter Masala", "Garlic Naan"), consolidated.map { it.itemName })
    }

    @Test
    fun `keeps the same menu item separate when the variants differ`() {
        val bill = billWith(
            listOf(
                item(11, menuItemId = 5, name = "Paneer Butter Masala", qty = 2, price = "210.00", variantId = 1L),
                item(57, menuItemId = 5, name = "Paneer Butter Masala", qty = 1, price = "230.00", variantId = 2L)
            )
        )

        val consolidated = bill.getConsolidatedItems()

        assertEquals(2, consolidated.size)
        assertEquals(listOf(2, 1), consolidated.map { it.quantity })
    }

    @Test
    fun `passes a single row through unchanged`() {
        val bill = billWith(
            listOf(item(11, menuItemId = 5, name = "Masala Chai", qty = 1, price = "40.00"))
        )

        val consolidated = bill.getConsolidatedItems()

        assertEquals(1, consolidated.size)
        assertEquals("40.00", consolidated[0].itemTotal)
    }

    @Test
    fun `sums amounts across many delta rows for one item`() {
        val bill = billWith(
            listOf(
                item(11, menuItemId = 5, name = "Masala Chai", qty = 1, price = "40.00"),
                item(57, menuItemId = 5, name = "Masala Chai", qty = 1, price = "40.00"),
                item(58, menuItemId = 5, name = "Masala Chai", qty = 2, price = "40.00")
            )
        )

        val consolidated = bill.getConsolidatedItems()

        assertEquals(1, consolidated.size)
        assertEquals(4, consolidated[0].quantity)
        assertEquals("160.00", consolidated[0].itemTotal)
    }
}
