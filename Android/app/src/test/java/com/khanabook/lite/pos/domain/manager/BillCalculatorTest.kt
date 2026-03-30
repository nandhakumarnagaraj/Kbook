package com.khanabook.lite.pos.domain.manager

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
}
