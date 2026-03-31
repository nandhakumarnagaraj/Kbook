package com.khanabook.lite.pos.domain.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.manager.QrCodeManager
import io.mockk.*
import org.junit.After
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
        assertTrue(output.contains("NET AMT:"))
        assertTrue(!output.contains("SCAN TO PAY"))
    }

    @Test
    fun `test 80mm layout output`() {
        val bill = getMockBill()
        val profile = RestaurantProfileEntity(paperSize = "80mm", shopName = "Test Shop", upiHandle = "test@upi")
        
        val bytes = InvoiceFormatter.formatForThermalPrinter(bill, profile)
        val output = String(bytes, Charset.forName("GBK"))
        
        // 80mm = 48 chars
        val expectedLine = "-".repeat(48)
        val expectedDoubleLine = "=".repeat(48)
        
        assertTrue("80mm single line missing", output.contains(expectedLine))
        assertTrue("80mm double line missing", output.contains(expectedDoubleLine))
        assertTrue(output.contains("ITEM"))
        assertTrue(output.contains("NET AMT:"))
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
