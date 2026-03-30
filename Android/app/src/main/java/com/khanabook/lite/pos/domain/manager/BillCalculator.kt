package com.khanabook.lite.pos.domain.manager


import java.math.BigDecimal
import java.math.RoundingMode

object BillCalculator {

    data class GstBreakdown(
        val cgst: String,
        val sgst: String,
        val totalGst: String
    )

    fun calculateSubtotal(items: List<Pair<String, Int>>): String {
        var subtotal = BigDecimal.ZERO
        items.forEach { (price, qty) ->
            val itemTotal = BigDecimal(price).multiply(BigDecimal.valueOf(qty.toLong()))
            subtotal = subtotal.add(itemTotal)
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP).toString()
    }

    fun calculateGST(subtotal: String, gstPct: Double, isInclusive: Boolean = false): GstBreakdown {
        val bdSubtotal = BigDecimal(subtotal)
        val bdGstPct = BigDecimal.valueOf(gstPct)
        val bd100 = BigDecimal.valueOf(100)
        
        val totalGst = if (isInclusive) {
            
            val multiplier = BigDecimal.ONE.add(bdGstPct.divide(bd100, 4, RoundingMode.HALF_UP))
            val baseAmount = bdSubtotal.divide(multiplier, 2, RoundingMode.HALF_UP)
            bdSubtotal.subtract(baseAmount)
        } else {
            
            bdSubtotal.multiply(bdGstPct).divide(bd100, 2, RoundingMode.HALF_UP)
        }
        
        val cgst = totalGst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
        val sgst = totalGst.subtract(cgst)
        
        return GstBreakdown(cgst.toString(), sgst.toString(), totalGst.toString())
    }

    fun calculateCustomTax(subtotal: String, taxPct: Double): String {
        val bdSubtotal = BigDecimal(subtotal)
        val bdTaxPct = BigDecimal.valueOf(taxPct)
        
        return bdSubtotal.multiply(bdTaxPct)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .toString()
    }

    fun calculateTotal(
        subtotal: String,
        cgst: String,
        sgst: String,
        customTax: String,
        isInclusive: Boolean = false
    ): String {
        return if (isInclusive) {
            
            BigDecimal(subtotal).setScale(2, RoundingMode.HALF_UP).toString()
        } else {
            val total = BigDecimal(subtotal)
                .add(BigDecimal(cgst))
                .add(BigDecimal(sgst))
                .add(BigDecimal(customTax))
            total.setScale(2, RoundingMode.HALF_UP).toString()
        }
    }

    fun validatePartPayment(a1: String, a2: String, total: String): Boolean {
        return try {
            val sum = BigDecimal(a1.ifBlank { "0" }).add(BigDecimal(a2.ifBlank { "0" }))
                .setScale(2, RoundingMode.HALF_UP)
            val bdTotal = BigDecimal(total.ifBlank { "0" }).setScale(2, RoundingMode.HALF_UP)

            sum.compareTo(bdTotal) == 0
        } catch (e: Exception) {
            false
        }
    }
    
    fun toFixedString(value: Double): String {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toString()
    }
}
