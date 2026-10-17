package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.payments.domain.QrCodeManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeManagerTest {

    @Test
    fun `buildUpiUri formats complete NPCI UPI URI with payee name and amount`() {
        val uri = QrCodeManager.buildUpiUri(
            vpa = "khanabook@okaxis",
            name = "KhanaBook POS",
            amount = 150.50
        )

        assertEquals("upi://pay?pa=khanabook%40okaxis&pn=KhanaBook+POS&am=150.50&cu=INR", uri)
    }

    @Test
    fun `buildUpiUri trims whitespace from VPA and encodes special characters`() {
        val uri = QrCodeManager.buildUpiUri(
            vpa = "  restaurant.biryani@icici  ",
            name = "Biryani & Kabab House",
            amount = 450.0
        )

        assertTrue(uri.startsWith("upi://pay?pa=restaurant.biryani%40icici"))
        assertTrue(uri.contains("&pn=Biryani+%26+Kabab+House"))
        assertTrue(uri.contains("&am=450.00"))
        assertTrue(uri.endsWith("&cu=INR"))
    }

    @Test
    fun `buildUpiUri omits payee name when empty or blank`() {
        val uri = QrCodeManager.buildUpiUri(
            vpa = "merchant@upi",
            name = "   ",
            amount = 99.0
        )

        assertEquals("upi://pay?pa=merchant%40upi&am=99.00&cu=INR", uri)
        assertFalse(uri.contains("&pn="))
    }

    @Test
    fun `buildUpiUri omits amount when zero for open customer-entered payment`() {
        val uri = QrCodeManager.buildUpiUri(
            vpa = "counter@hdfcbank",
            name = "Cafe Counter",
            amount = 0.0
        )

        assertEquals("upi://pay?pa=counter%40hdfcbank&pn=Cafe+Counter&cu=INR", uri)
        assertFalse(uri.contains("&am="))
    }

    @Test
    fun `buildUpiUri formats amount with exactly two decimal places`() {
        val uri = QrCodeManager.buildUpiUri(
            vpa = "test@upi",
            name = "Test",
            amount = 1.0
        )

        assertTrue(uri.contains("&am=1.00"))
    }
}