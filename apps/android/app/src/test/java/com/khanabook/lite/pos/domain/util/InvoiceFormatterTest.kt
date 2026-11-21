package com.khanabook.lite.pos.domain.util
import com.khanabook.lite.pos.feature.billing.domain.InvoiceFormatter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.payments.domain.QrCodeManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

class InvoiceFormatterTest {

    @Before
    fun setup() {
        mockkObject(QrCodeManager)
        mockkStatic(BitmapFactory::class)
        mockkStatic(Bitmap::class)
        
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { QrCodeManager.generateQr(any(), any()) } returns mockBitmap
        every { QrCodeManager.generateUpiQr(any(), any(), any(), any()) } returns mockBitmap
        every { BitmapFactory.decodeFile(any()) } returns mockBitmap
        every { BitmapFactory.decodeFile(any(), any()) } returns mockBitmap
        every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns mockBitmap
    }

    @After
    fun tearDown() {
        unmockkObject(QrCodeManager)
        unmockkStatic(BitmapFactory::class)
        unmockkStatic(Bitmap::class)
    }

    private fun getMockBill(): BillWithItems {
        val bill = BillEntity(
            id = 1,
            lifetimeOrderId = 1001,
            dailyOrderId = 1,
            dailyOrderDisplay = "001",
            createdAt = System.currentTimeMillis(),
            subtotal = "200.00",
            totalAmount = "200.00",
            paymentMode = "upi",
            customerName = "Test Customer",
            customerWhatsapp = "9876543210",
            paymentStatus = "paid",
            orderStatus = "completed"
        )
        val items = listOf(
            BillItemEntity(id = 1, billId = 1, menuItemId = 1, itemName = "Chicken Biriyani", price = "150.0", quantity = 1, itemTotal = "150.0"),
            BillItemEntity(id = 2, billId = 1, menuItemId = 2, itemName = "Coke", price = "50.0", quantity = 1, itemTotal = "50.0")
        )
        return BillWithItems(bill = bill, items = items, payments = emptyList())
    }

    @Test
    fun `test 58mm layout output`() {
        val bill = getMockBill()
        val profile = RestaurantProfileEntity(paperSize = "58mm", shopName = "Test Shop", upiHandle = "test@upi")
        
        val bytes = InvoiceFormatter.formatForThermalPrinter(bill, profile)
        val output = String(bytes, Charset.forName("GBK"))
        
        // 58mm = 32 chars
        val expectedLine = "-".repeat(32)
        val expectedDoubleLine = "=".repeat(32)
        
        assertTrue("58mm single line missing", output.contains(expectedLine))
        assertTrue("58mm double line missing", output.contains(expectedDoubleLine))
        assertTrue(output.contains("ITEM"))
        assertTrue(output.contains("Sub-total:"))
    }

    @Test
    fun `test 80mm layout output`() {
        val bill = getMockBill()
        val profile = RestaurantProfileEntity(paperSize = "80mm", shopName = "Test Shop", upiHandle = "test@upi")
        
        val bytes = InvoiceFormatter.formatForThermalPrinter(bill, profile)
        val output = String(bytes, Charset.forName("GBK"))
        
        // 80mm = 40 chars
        val expectedLine = "-".repeat(40)
        val expectedDoubleLine = "=".repeat(40)
        
        assertTrue("80mm single line missing", output.contains(expectedLine))
        assertTrue("80mm double line missing", output.contains(expectedDoubleLine))
        assertTrue(output.contains("ITEM"))
        assertTrue(output.contains("Sub-total:"))
    }

    private fun getTaxedBill(cgst: String, sgst: String, customTax: String = "0.0", gstPct: String = "5.0"): BillWithItems {
        val base = getMockBill()
        return base.copy(
            bill = base.bill.copy(
                cgstAmount = cgst,
                sgstAmount = sgst,
                customTaxAmount = customTax,
                gstPercentage = gstPct
            )
        )
    }

    private fun render(bill: BillWithItems, profile: RestaurantProfileEntity): String =
        String(InvoiceFormatter.formatForThermalPrinter(bill, profile), Charset.forName("GBK"))

    @Test
    fun `gst disabled hides gstin and tax invoice label`() {
        // BUG-034: a stored GSTIN is kept for later re-enabling, so the receipt must gate on
        // gstEnabled rather than on the field being non-blank.
        val profile = RestaurantProfileEntity(gstEnabled = false, gstin = "27AAAAA0000A1Z5")
        val output = render(getMockBill(), profile)

        assertFalse("GSTIN must not print when GST is off", output.contains("GSTIN"))
        assertTrue(output.contains("INVOICE NO"))
        assertFalse(output.contains("TAX INVOICE NO"))
        assertFalse(output.contains("CGST"))
    }

    @Test
    fun `gst enabled prints gstin and split tax`() {
        val profile = RestaurantProfileEntity(gstEnabled = true, gstin = "27AAAAA0000A1Z5", gstPercentage = 5.0)
        val output = render(getTaxedBill(cgst = "5.00", sgst = "5.00"), profile)

        assertTrue(output.contains("GSTIN: 27AAAAA0000A1Z5"))
        assertTrue(output.contains("TAX INVOICE NO"))
        assertTrue(output.contains("CGST (2.5%)"))
        assertTrue(output.contains("SGST (2.5%)"))
    }

    @Test
    fun `reprint after gst is turned off hides tax lines and gstin`() {
        // Pinned policy (same as GenerateBillTextTest): GST output follows the shop setting, so a
        // taxed bill reprinted after GST is disabled shows no GST text. The consequence - its
        // stored total still contains that tax - is BUG-036, an open decision for the owner.
        val profile = RestaurantProfileEntity(gstEnabled = false, gstin = "27AAAAA0000A1Z5")
        val output = render(getTaxedBill(cgst = "5.00", sgst = "5.00"), profile)

        assertFalse("CGST must not print when GST is off", output.contains("CGST"))
        assertFalse("SGST must not print when GST is off", output.contains("SGST"))
        assertFalse("GSTIN must not print when GST is off", output.contains("GSTIN"))
        assertTrue(output.contains("INVOICE NO"))
        assertTrue(output.contains("GRAND TOTAL"))
    }

    @Test
    fun `custom tax row prints when the bill carries it`() {
        val profile = RestaurantProfileEntity(gstEnabled = false, customTaxName = "Service Tax")
        val output = render(getTaxedBill(cgst = "0.0", sgst = "0.0", customTax = "10.00"), profile)

        assertTrue(output.contains("Service Tax:"))
        assertTrue(output.contains("10.00"))
    }

    @Test
    fun `custom tax row is absent when the bill carries none`() {
        val profile = RestaurantProfileEntity(gstEnabled = false, customTaxName = "Service Tax")
        val output = render(getMockBill(), profile)

        assertFalse(output.contains("Service Tax"))
    }

    @Test
    fun `test review QR presence`() {
        val bill = getMockBill()
        val profile = RestaurantProfileEntity(reviewUrl = "https://google.com/review")
        
        val bytes = InvoiceFormatter.formatForThermalPrinter(bill, profile)
        val output = String(bytes, Charset.forName("GBK"))
        
        assertTrue(output.contains("RATE US / FEEDBACK"))
    }
}