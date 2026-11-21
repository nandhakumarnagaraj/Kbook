package com.khanabook.lite.pos.feature.reports.domain

import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Payment breakdown arithmetic, including the refund handling added with BUG-043.
 *
 * The breakdown feeds the Reports screen, so its per-mode rows have to add up to the same net
 * figure the Home revenue tile shows. Refunds are taken out of the part-payment legs first-leg
 * first, which keeps each leg non-negative and their sum equal to the netted mode row.
 */
class ReportGeneratorPaymentBreakdownTest {

    private val generator = ReportGenerator(mockk<BillRepository>(relaxed = true))

    private fun partBill(
        part1: String,
        part2: String,
        total: String,
        refund: String? = null
    ) = BillEntity(
        id = 1,
        lifetimeOrderId = 1,
        dailyOrderId = 1,
        dailyOrderDisplay = "2026-11-20-1",
        subtotal = total,
        totalAmount = total,
        paymentMode = "part_cash_upi",
        paymentStatus = "success",
        orderStatus = "completed",
        partAmount1 = part1,
        partAmount2 = part2,
        refundAmount = refund
    )

    @Test
    fun `part payment legs add up to the mode total`() {
        val breakdown = generator.buildPaymentBreakdown(listOf(partBill("60.00", "40.00", "100.00")))

        assertEquals("60.00", breakdown["Cash"])
        assertEquals("40.00", breakdown["UPI"])
        assertEquals("100.00", breakdown["Cash + UPI"])
    }

    @Test
    fun `refund is taken out of the legs so they still add up`() {
        val breakdown = generator.buildPaymentBreakdown(
            listOf(partBill("60.00", "40.00", "100.00", refund = "20.00"))
        )

        val legs = BigDecimal(breakdown["Cash"]).add(BigDecimal(breakdown["UPI"]))
        assertEquals("80.00", breakdown["Cash + UPI"])
        assertEquals(BigDecimal("80.00"), legs)
    }

    @Test
    fun `refund larger than the first leg spills into the second`() {
        val breakdown = generator.buildPaymentBreakdown(
            listOf(partBill("30.00", "70.00", "100.00", refund = "50.00"))
        )

        assertEquals("0.00", breakdown["Cash"])
        assertEquals("50.00", breakdown["UPI"])
        assertEquals("50.00", breakdown["Cash + UPI"])
    }
}
