package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.feature.billing.domain.BillCalculator
import org.junit.Test
import org.junit.Assert.*

class BillCalculatorTest {

    @Test
    fun testSubtotalCalculation_BigDecimalPrecision() {
        val items = listOf(
            "10.555" to 1, 
            "20.123" to 2  
        )
        val subtotal = BillCalculator.calculateSubtotal(items)
        assertEquals("50.80", subtotal)
    }

    @Test
    fun testGSTSplit_CGST_SGST_Equality() {
        val subtotal = "100.00"
        val gstPct = 18.0
        val breakdown = BillCalculator.calculateGST(subtotal, gstPct)
        assertEquals("9.00", breakdown.cgst)
        assertEquals("9.00", breakdown.sgst)
        assertEquals("18.00", breakdown.totalGst)
    }

    @Test
    fun testGSTSplit_OddTotal() {
        val subtotal = "58.61"
        val gstPct = 18.0
        val breakdown = BillCalculator.calculateGST(subtotal, gstPct)
        assertEquals("10.55", breakdown.totalGst)
        assertEquals("5.28", breakdown.cgst)
        assertEquals("5.27", breakdown.sgst)
    }

    @Test
    fun testTotalCalculation() {
        val subtotal = "100.00"
        val cgst = "9.00"
        val sgst = "9.00"
        val customTax = "0.00"
        val total = BillCalculator.calculateTotal(subtotal, cgst, sgst, customTax)
        assertEquals("118.00", total)
    }

    @Test
    fun testSuggestedCashDenominations_340() {
        val suggestions = BillCalculator.getSuggestedCashDenominations(340.0)
        // Expect exact 340, next 50 (350), next 100 (400), and common note (500)
        assertEquals(listOf(340L, 350L, 400L, 500L), suggestions)
    }

    @Test
    fun testSuggestedCashDenominations_50() {
        val suggestions = BillCalculator.getSuggestedCashDenominations(50.0)
        // 50 is exact, next 100, 200, 500
        assertEquals(listOf(50L, 100L, 200L, 500L), suggestions)
    }

    @Test
    fun testSuggestedCashDenominations_ZeroOrNegative() {
        assertTrue(BillCalculator.getSuggestedCashDenominations(0.0).isEmpty())
        assertTrue(BillCalculator.getSuggestedCashDenominations(-50.0).isEmpty())
    }

    @Test
    fun testCashTenderCalculation_ChangeDue() {
        val result = BillCalculator.calculateCashTender(340.0, "500")
        assertEquals(160.0, result.changeDue, 0.001)
        assertEquals(0.0, result.shortage, 0.001)
        assertTrue(result.isExactOrMore)
        assertFalse(result.isPartial)
    }

    @Test
    fun testCashTenderCalculation_Shortage() {
        val result = BillCalculator.calculateCashTender(340.0, "300")
        assertEquals(0.0, result.changeDue, 0.001)
        assertEquals(40.0, result.shortage, 0.001)
        assertFalse(result.isExactOrMore)
        assertTrue(result.isPartial)
    }

    @Test
    fun testCashTenderCalculation_Exact() {
        val result = BillCalculator.calculateCashTender(340.0, "340")
        assertEquals(0.0, result.changeDue, 0.001)
        assertEquals(0.0, result.shortage, 0.001)
        assertTrue(result.isExactOrMore)
        assertFalse(result.isPartial)
    }
}
