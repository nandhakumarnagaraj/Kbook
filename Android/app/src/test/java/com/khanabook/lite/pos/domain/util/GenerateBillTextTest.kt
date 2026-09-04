package com.khanabook.lite.pos.domain.util

import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for the shared SMS/WhatsApp invoice text builder.
 * generateBillText touches no Android framework, so it runs on the JVM directly.
 */
class GenerateBillTextTest {

    private fun bill() = BillWithItems(
        bill = BillEntity(
            id = 1, lifetimeOrderId = 1, dailyOrderId = 7, dailyOrderDisplay = "2026-09-03-7",
            subtotal = "200.00", gstPercentage = "5", cgstAmount = "5.00", sgstAmount = "5.00",
            totalAmount = "210.00", paymentMode = "cash", paymentStatus = "paid", orderStatus = "completed"
        ),
        items = listOf(
            BillItemEntity(id = 1, billId = 1, menuItemId = 1, itemName = "Thali", price = "200.0", quantity = 1, itemTotal = "200.0")
        ),
        payments = emptyList()
    )

    @Test
    fun `gst-enabled text has CGST SGST SAC and state code`() {
        val profile = RestaurantProfileEntity(
            shopName = "Test Shop", gstEnabled = true, gstin = "29ABCDE1234F1Z5"
        )
        val text = generateBillText(bill(), profile)

        assertTrue("sub-total missing", text.contains("Sub-total:"))
        assertTrue("CGST missing", text.contains("CGST (2.5%):"))
        assertTrue("SGST missing", text.contains("SGST (2.5%):"))
        assertTrue("SAC missing", text.contains("SAC: 996331"))
        assertTrue("GSTIN missing", text.contains("GSTIN: 29ABCDE1234F1Z5"))
        assertTrue("State Code missing", text.contains("State Code: 29"))
        assertTrue("Tax Invoice label missing", text.contains("Tax Invoice No"))
    }

    @Test
    fun `gst-disabled text omits GSTIN SAC and tax lines`() {
        val profile = RestaurantProfileEntity(
            shopName = "Test Shop", gstEnabled = false, gstin = "29ABCDE1234F1Z5"
        )
        val text = generateBillText(bill(), profile)

        assertFalse("GSTIN must not leak when GST disabled", text.contains("GSTIN:"))
        assertFalse("State Code must not print when GST disabled", text.contains("State Code:"))
        assertFalse("SAC must not print when GST disabled", text.contains("SAC:"))
        assertFalse("CGST must not print when GST disabled", text.contains("CGST"))
        assertTrue("Invoice label expected", text.contains("Invoice No"))
        assertTrue("total still present", text.contains("Total Amount:"))
    }
}
