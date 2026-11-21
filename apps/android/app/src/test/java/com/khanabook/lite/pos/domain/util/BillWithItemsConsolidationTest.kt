package com.khanabook.lite.pos.domain.util

import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for BUG-044. `bill_items.menu_item_id` is `SET_NULL` on menu-item delete, so
 * grouping on `(menuItemId ?: 0L, variantId)` merged every deleted dish in a bill into one line.
 */
class BillWithItemsConsolidationTest {

    private fun bill(items: List<BillItemEntity>) = BillWithItems(
        bill = BillEntity(
            id = 1,
            dailyOrderId = 1,
            dailyOrderDisplay = "1",
            lifetimeOrderId = 1,
            subtotal = "0.00",
            totalAmount = "0.00",
            paymentMode = "cash",
            paymentStatus = "success",
            orderStatus = "completed"
        ),
        items = items,
        payments = emptyList()
    )

    private fun item(
        id: Long,
        name: String,
        price: String,
        qty: Int,
        total: String,
        menuItemId: Long? = 7,
        variantId: Long? = null
    ) = BillItemEntity(
        id = id,
        billId = 1,
        menuItemId = menuItemId,
        itemName = name,
        variantId = variantId,
        price = price,
        quantity = qty,
        itemTotal = total
    )

    @Test
    fun `two deleted dishes stay two lines`() {
        val consolidated = bill(
            listOf(
                item(1, "Paneer Tikka", "250.00", 1, "250.00", menuItemId = null),
                item(2, "Dal Makhani", "180.00", 1, "180.00", menuItemId = null)
            )
        ).getConsolidatedItems()

        assertEquals(2, consolidated.size)
        assertEquals(listOf("Paneer Tikka", "Dal Makhani"), consolidated.map { it.itemName })
        // Totals must not be summed across different dishes.
        assertEquals(listOf("250.00", "180.00"), consolidated.map { it.itemTotal })
    }

    @Test
    fun `identical rows of a live dish still merge`() {
        val consolidated = bill(
            listOf(
                item(1, "Chai", "20.00", 1, "20.00"),
                item(2, "Chai", "20.00", 2, "40.00")
            )
        ).getConsolidatedItems()

        assertEquals(1, consolidated.size)
        assertEquals(3, consolidated.first().quantity)
        assertEquals("60.00", consolidated.first().itemTotal)
    }

    @Test
    fun `deleted dish with the same name merges only with itself`() {
        val consolidated = bill(
            listOf(
                item(1, "Chai", "20.00", 1, "20.00", menuItemId = null),
                item(2, "Chai", "20.00", 1, "20.00", menuItemId = null),
                item(3, "Samosa", "15.00", 1, "15.00", menuItemId = null)
            )
        ).getConsolidatedItems()

        assertEquals(2, consolidated.size)
        assertEquals(2, consolidated.first { it.itemName == "Chai" }.quantity)
    }

    @Test
    fun `same dish at two prices stays two lines`() {
        val consolidated = bill(
            listOf(
                item(1, "Thali", "150.00", 1, "150.00"),
                item(2, "Thali", "170.00", 1, "170.00")
            )
        ).getConsolidatedItems()

        assertEquals(2, consolidated.size)
    }
}
