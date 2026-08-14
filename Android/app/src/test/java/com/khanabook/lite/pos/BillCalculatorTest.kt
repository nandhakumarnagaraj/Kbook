package com.khanabook.lite.pos

import com.khanabook.lite.pos.domain.manager.BillCalculator
import org.junit.Assert.*
import org.junit.Test

class BillCalculatorTest {

    // ── Subtotal Calculation ──────────────────────────────────────────────────

    @Test
    fun `subtotal of single item`() {
        val items = listOf("120.00" to 1)
        assertEquals("120.00", BillCalculator.calculateSubtotal(items))
    }

    @Test
    fun `subtotal of multiple items with quantities`() {
        val items = listOf("100.00" to 2, "50.00" to 3)
        assertEquals("350.00", BillCalculator.calculateSubtotal(items))
    }

    @Test
    fun `subtotal with decimal prices`() {
        val items = listOf("99.50" to 2, "15.75" to 1)
        assertEquals("214.75", BillCalculator.calculateSubtotal(items))
    }

    @Test
    fun `subtotal of empty cart is zero`() {
        val items = emptyList<Pair<String, Int>>()
        assertEquals("0.00", BillCalculator.calculateSubtotal(items))
    }

    @Test
    fun `subtotal with large quantities (Indian wedding order)`() {
        val items = listOf("250.00" to 100, "80.00" to 200)
        assertEquals("41000.00", BillCalculator.calculateSubtotal(items))
    }

    // ── GST Calculation (Exclusive — standard Indian restaurant billing) ─────

    @Test
    fun `5 percent GST on 1000 rupees (exclusive)`() {
        val gst = BillCalculator.calculateGST("1000.00", 5.0, isInclusive = false)
        assertEquals("25.00", gst.cgst)
        assertEquals("25.00", gst.sgst)
        assertEquals("50.00", gst.totalGst)
    }

    @Test
    fun `18 percent GST on 500 rupees (exclusive)`() {
        val gst = BillCalculator.calculateGST("500.00", 18.0, isInclusive = false)
        assertEquals("45.00", gst.cgst)
        assertEquals("45.00", gst.sgst)
        assertEquals("90.00", gst.totalGst)
    }

    @Test
    fun `0 percent GST`() {
        val gst = BillCalculator.calculateGST("1000.00", 0.0, isInclusive = false)
        assertEquals("0.00", gst.cgst)
        assertEquals("0.00", gst.sgst)
        assertEquals("0.00", gst.totalGst)
    }

    @Test
    fun `GST on odd amount (rounding)`() {
        val gst = BillCalculator.calculateGST("333.00", 5.0, isInclusive = false)
        assertEquals("8.33", gst.cgst)
        assertEquals("8.32", gst.sgst)
        assertEquals("16.65", gst.totalGst)
    }

    // ── GST Calculation (Inclusive — MRP-style billing) ───────────────────────

    @Test
    fun `5 percent GST inclusive on 525 rupees`() {
        val gst = BillCalculator.calculateGST("525.00", 5.0, isInclusive = true)
        assertEquals("12.50", gst.cgst)
        assertEquals("12.50", gst.sgst)
        assertEquals("25.00", gst.totalGst)
    }

    @Test
    fun `18 percent GST inclusive on 590 rupees`() {
        val gst = BillCalculator.calculateGST("590.00", 18.0, isInclusive = true)
        assertEquals("45.00", gst.cgst)
        assertEquals("45.00", gst.sgst)
        assertEquals("90.00", gst.totalGst)
    }

    // ── Total Calculation ─────────────────────────────────────────────────────

    @Test
    fun `total with exclusive GST adds all components`() {
        val total = BillCalculator.calculateTotal("1000.00", "25.00", "25.00", "0.00", isInclusive = false)
        assertEquals("1050.00", total)
    }

    @Test
    fun `total with inclusive GST returns subtotal unchanged`() {
        val total = BillCalculator.calculateTotal("525.00", "12.50", "12.50", "0.00", isInclusive = true)
        assertEquals("525.00", total)
    }

    @Test
    fun `total with custom tax (service charge)`() {
        val total = BillCalculator.calculateTotal("1000.00", "25.00", "25.00", "100.00", isInclusive = false)
        assertEquals("1150.00", total)
    }

    // ── Part Payment Validation ───────────────────────────────────────────────

    @Test
    fun `valid part payment sums to total`() {
        assertTrue(BillCalculator.validatePartPayment("500.00", "550.00", "1050.00"))
    }

    @Test
    fun `invalid part payment does not sum to total`() {
        assertFalse(BillCalculator.validatePartPayment("500.00", "500.00", "1050.00"))
    }

    @Test
    fun `part payment with blank amounts`() {
        assertFalse(BillCalculator.validatePartPayment("", "", "1050.00"))
    }

    @Test
    fun `part payment with one blank amount`() {
        assertTrue(BillCalculator.validatePartPayment("1050.00", "", "1050.00"))
    }

    // ── Part Payment Split ────────────────────────────────────────────────────

    @Test
    fun `split even amount`() {
        val (first, second) = BillCalculator.splitPartPayment("1000.00")
        assertEquals("500.00", first)
        assertEquals("500.00", second)
    }

    @Test
    fun `split odd amount rounds correctly`() {
        val (first, second) = BillCalculator.splitPartPayment("1001.00")
        assertEquals("500.50", first)
        assertEquals("500.50", second)
    }

    @Test
    fun `split amount with paisa`() {
        val (first, second) = BillCalculator.splitPartPayment("99.99")
        assertEquals("49.99", first)
        assertEquals("50.00", second)
    }

    // ── Custom Tax ────────────────────────────────────────────────────────────

    @Test
    fun `10 percent service charge`() {
        assertEquals("100.00", BillCalculator.calculateCustomTax("1000.00", 10.0))
    }

    @Test
    fun `zero custom tax`() {
        assertEquals("0.00", BillCalculator.calculateCustomTax("1000.00", 0.0))
    }

    // ── Edge Cases ────────────────────────────────────────────────────────────

    @Test
    fun `very small bill (chai for 10 rupees)`() {
        val items = listOf("10.00" to 1)
        val subtotal = BillCalculator.calculateSubtotal(items)
        val gst = BillCalculator.calculateGST(subtotal, 5.0)
        val total = BillCalculator.calculateTotal(subtotal, gst.cgst, gst.sgst, "0.00")
        assertEquals("10.00", subtotal)
        assertEquals("0.50", gst.totalGst)
        assertEquals("10.50", total)
    }

    @Test
    fun `large catering bill (1 lakh)`() {
        val items = listOf("500.00" to 200)
        val subtotal = BillCalculator.calculateSubtotal(items)
        val gst = BillCalculator.calculateGST(subtotal, 5.0)
        val total = BillCalculator.calculateTotal(subtotal, gst.cgst, gst.sgst, "0.00")
        assertEquals("100000.00", subtotal)
        assertEquals("5000.00", gst.totalGst)
        assertEquals("105000.00", total)
    }
}
