package com.khanabook.lite.pos.feature.billing.domain

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.payments.domain.PaymentLimits
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
            val itemPrice = price.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val itemTotal = itemPrice.multiply(BigDecimal.valueOf(qty.toLong()))
            subtotal = subtotal.add(itemTotal)
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP).toString()
    }

    fun calculateGST(subtotal: String, gstPct: Double, isInclusive: Boolean = false): GstBreakdown {
        val bdSubtotal = subtotal.toBigDecimalOrNull() ?: BigDecimal.ZERO
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

    /**
     * Payable total for one cart.
     *
     * With inclusive pricing the GST is already inside `subtotal`: it is printed for the customer
     * but must NOT be added again. Custom tax is never embedded in the price (it applies only while
     * GST is off), so it is always added. Previously the inclusive branch returned the subtotal
     * alone and dropped custom tax, and neither production caller passed the flag at all — a
     * tax-inclusive shop was therefore charged its GST twice (registered as BUG-041).
     */
    fun calculateTotal(
        subtotal: String,
        cgst: String,
        sgst: String,
        customTax: String,
        isInclusive: Boolean = false
    ): String {
        val bdSubtotal = subtotal.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val gst = if (isInclusive) {
            BigDecimal.ZERO
        } else {
            (cgst.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                .add(sgst.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
        val total = bdSubtotal
            .add(gst)
            .add(customTax.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        return total.setScale(2, RoundingMode.HALF_UP).toString()
    }

    /** The tax components and the payable total for one cart. */
    data class BillTotals(
        val subtotal: String,
        val cgst: String,
        val sgst: String,
        val customTax: String,
        val total: String
    )

    /**
     * Single source of truth for the bill arithmetic used by both the cart summary
     * (`CartManager.computeSummary`) and the persisted bill recalc in `BillingViewModel`.
     *
     * GST applies when the shop has it enabled, custom tax only otherwise - never both, because
     * `customTaxPercentage` is the counterpart of the GST toggle. `isTaxInclusive` is honoured
     * end to end here: it changes how the GST rows are derived AND whether they are added to the
     * total, which is the pair of decisions that must always be made together.
     */
    fun calculateBillTotals(
        items: List<Pair<String, Int>>,
        profile: RestaurantProfileEntity?
    ): BillTotals {
        val subtotal = calculateSubtotal(items)
        val inclusive = profile?.isTaxInclusive == true
        var cgst = "0.00"
        var sgst = "0.00"
        var customTax = "0.00"

        if (profile?.gstEnabled == true) {
            val gst = calculateGST(subtotal, profile.gstPercentage, inclusive)
            cgst = gst.cgst
            sgst = gst.sgst
        } else {
            val customPct = profile?.customTaxPercentage ?: 0.0
            if (customPct > 0.0) customTax = calculateCustomTax(subtotal, customPct)
        }

        return BillTotals(
            subtotal = subtotal,
            cgst = cgst,
            sgst = sgst,
            customTax = customTax,
            total = calculateTotal(subtotal, cgst, sgst, customTax, inclusive)
        )
    }

    fun validatePartPayment(a1: String, a2: String, total: String): Boolean {
        return try {
            val bd1 = a1.ifBlank { "0" }.toBigDecimalOrNull() ?: return false
            val bd2 = a2.ifBlank { "0" }.toBigDecimalOrNull() ?: return false
            val bdTotal = total.ifBlank { "0" }.toBigDecimalOrNull() ?: return false

            if (bd1 < BigDecimal.ZERO || bd2 < BigDecimal.ZERO || bdTotal <= BigDecimal.ZERO) {
                return false
            }

            val sum = bd1.add(bd2).setScale(2, RoundingMode.HALF_UP)
            val scaledTotal = bdTotal.setScale(2, RoundingMode.HALF_UP)

            sum.compareTo(scaledTotal) == 0
        } catch (e: Exception) {
            false
        }
    }

    fun splitPartPayment(total: String): Pair<String, String> {
        val bdTotal = total.ifBlank { "0" }.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        val first = bdTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN)
        val second = bdTotal.subtract(first).setScale(2, RoundingMode.HALF_UP)
        return first.toString() to second.toString()
    }

    fun splitCashUpiWithUpiCap(total: String): Pair<String, String> {
        val bdTotal = parseAmount(total)
        val upi = bdTotal.min(PaymentLimits.UPI_SINGLE_TRANSACTION_MAX)
        val cash = bdTotal.subtract(upi).setScale(2, RoundingMode.HALF_UP)
        return cash.toString() to upi.toString()
    }

    fun splitUpiPosWithUpiCap(total: String): Pair<String, String> {
        val bdTotal = parseAmount(total)
        val upi = bdTotal.min(PaymentLimits.UPI_SINGLE_TRANSACTION_MAX)
        val pos = bdTotal.subtract(upi).setScale(2, RoundingMode.HALF_UP)
        return upi.toString() to pos.toString()
    }

    private fun parseAmount(value: String): BigDecimal {
        return value.ifBlank { "0" }.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?: BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    }
    
    fun toFixedString(value: Double): String {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toString()
    }

}
