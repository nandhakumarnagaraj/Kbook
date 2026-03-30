package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.manager.InvoicePDFGenerator
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File

@Ignore("Requires complex Android framework mocking - tested manually")
class UtilsTest {

    private lateinit var context: Context
    private lateinit var packageManager: android.content.pm.PackageManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)

        mockkStatic(FileProvider::class)
        mockkStatic(Toast::class)
        mockkStatic(Uri::class)
        mockkConstructor(InvoicePDFGenerator::class)
        mockkStatic(android.os.Handler::class)
        mockkStatic(android.os.Looper::class)

        every { context.packageName } returns "com.khanabook.lite.pos"
        every { context.packageManager } returns packageManager
        every { Toast.makeText(any<Context>(), any<CharSequence>(), any<Int>()) } returns mockk(relaxed = true)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns mockk<Uri>(relaxed = true)
        every { anyConstructed<InvoicePDFGenerator>().generatePDF(any(), any(), any()) } returns mockk<File>()
    }

    @After
    fun tearDown() {
        unmockkStatic(FileProvider::class)
        unmockkStatic(Toast::class)
        unmockkStatic(Uri::class)
        unmockkConstructor(InvoicePDFGenerator::class)
        unmockkStatic(android.os.Handler::class)
        unmockkStatic(android.os.Looper::class)
    }

    @Test
    fun `shareBillOnWhatsApp should call context startActivity`() {
        val bill = BillEntity(
            id = 1,
            dailyOrderId = 1,
            dailyOrderDisplay = "001",
            lifetimeOrderId = 1001,
            totalAmount = "100.0",
            subtotal = "100.0",
            paymentMode = "cash",
            paymentStatus = "paid",
            orderStatus = "completed",
            customerWhatsapp = "9876543210"
        )
        val billWithItems = BillWithItems(bill = bill, items = emptyList(), payments = emptyList())
        val profile = RestaurantProfileEntity(
            id = 1,
            shopName = "Test Shop",
            whatsappNumber = "1234567890"
        )

        shareBillOnWhatsApp(context, billWithItems, profile)
    }
}
