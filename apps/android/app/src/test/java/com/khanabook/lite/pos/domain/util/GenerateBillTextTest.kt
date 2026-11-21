package com.khanabook.lite.pos.domain.util
import com.khanabook.lite.pos.core.util.generateBillText

import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
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
    fun `gst rows print from the amounts even when the percentage is zero`() {
        // BUG-045: the rows used to be gated on gstPercentage, so a bill with real CGST/SGST
        // amounts but a 0 percentage lost its tax breakdown entirely.
        val taxed = bill().let { b ->
            b.copy(bill = b.bill.copy(gstPercentage = "0", cgstAmount = "5.00", sgstAmount = "5.00"))
        }
        val profile = RestaurantProfileEntity(shopName = "Test Shop", gstEnabled = true)
        val text = generateBillText(taxed, profile)

        assertTrue("CGST missing without a percentage", text.contains("CGST: Rs.5.00"))
        assertTrue("SGST missing without a percentage", text.contains("SGST: Rs.5.00"))
    }

    @Test
    fun `gst-disabled text shows the custom tax and sub-total`() {
        // BUG-045: a non-GST shop with a custom tax shared a bill whose total exceeded the item
        // sum with nothing explaining the difference.
        val withCustomTax = bill().let { b ->
            b.copy(bill = b.bill.copy(customTaxAmount = "10.00", totalAmount = "210.00"))
        }
        val profile = RestaurantProfileEntity(
            shopName = "Test Shop", gstEnabled = false, customTaxName = "Service Charge"
        )
        val text = generateBillText(withCustomTax, profile)

        assertTrue("Sub-total missing", text.contains("Sub-total: Rs.200.00"))
        assertTrue("custom tax row missing", text.contains("SERVICE CHARGE: Rs.10.00"))
        assertFalse("GSTIN must not leak when GST disabled", text.contains("GSTIN:"))
        assertFalse("CGST must not print when GST disabled", text.contains("CGST"))
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

    @Test
    fun `gst-disabled reprint of a taxed bill reconciles without GST words`() {
        // BUG-036: the total comes from the stored bill, so a bill charged under GST still has to
        // explain that component after the owner turns GST off - but it must not print GST labels.
        val profile = RestaurantProfileEntity(shopName = "Test Shop", gstEnabled = false)
        val text = generateBillText(bill(), profile)

        assertTrue("neutral tax row missing", text.contains("Taxes: Rs.10.00"))
        assertFalse("CGST must not print when GST disabled", text.contains("CGST"))
        assertFalse("SGST must not print when GST disabled", text.contains("SGST"))
    }

    @Test
    fun `tax-inclusive reprint labels the hidden tax as included`() {
        // With inclusive pricing the stored GST is already inside the sub-total, so the neutral row
        // must not read like an amount owed on top.
        val inclusiveBill = bill().let { b ->
            b.copy(bill = b.bill.copy(subtotal = "118.00", cgstAmount = "9.00", sgstAmount = "9.00", totalAmount = "118.00"))
        }
        val profile = RestaurantProfileEntity(shopName = "Test Shop", gstEnabled = false)
        val text = generateBillText(inclusiveBill, profile)

        assertTrue("inclusive tax row missing", text.contains("Taxes (included): Rs.18.00"))
        assertFalse("CGST must not print when GST disabled", text.contains("CGST"))
    }

    @Test
    fun `custom tax row survives after GST is enabled`() {
        // The row follows the bill: a bill charged a custom tax keeps its explanation even though
        // the shop has since switched to GST.
        val withCustomTax = bill().let { b ->
            b.copy(bill = b.bill.copy(cgstAmount = "0.00", sgstAmount = "0.00", customTaxAmount = "10.00"))
        }
        val profile = RestaurantProfileEntity(
            shopName = "Test Shop", gstEnabled = true, customTaxName = "Service Charge"
        )
        val text = generateBillText(withCustomTax, profile)

        assertTrue("custom tax row missing", text.contains("SERVICE CHARGE: Rs.10.00"))
    }
}