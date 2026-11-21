package com.khanabook.lite.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for BUG-042: server spellings used to be stored verbatim while local readers
 * compared fixed lowercase literals, so a pulled "PAID" bill read as a draft on the second
 * terminal. These cases pin the alias table used by both the pull path and the enums.
 */
class RemoteStatusNormalizerTest {

    @Test
    fun `paid and title case spellings read as completed`() {
        assertEquals("completed", RemoteStatusNormalizer.orderStatus("PAID"))
        assertEquals("completed", RemoteStatusNormalizer.orderStatus("Completed"))
        assertEquals("completed", RemoteStatusNormalizer.orderStatus(" settled "))
        assertEquals("completed", RemoteStatusNormalizer.orderStatus("closed"))
    }

    @Test
    fun `a refunded bill stays a completed sale`() {
        // The refund is tracked in refund_amount; reclassifying the order as cancelled would
        // silently remove it from revenue.
        assertEquals("completed", RemoteStatusNormalizer.orderStatus("refunded"))
    }

    @Test
    fun `void spellings read as cancelled`() {
        assertEquals("cancelled", RemoteStatusNormalizer.orderStatus("VOID"))
        assertEquals("cancelled", RemoteStatusNormalizer.orderStatus("Canceled"))
    }

    @Test
    fun `open spellings read as draft`() {
        assertEquals("draft", RemoteStatusNormalizer.orderStatus("Preparing"))
        assertEquals("draft", RemoteStatusNormalizer.orderStatus("PENDING"))
    }

    @Test
    fun `blank order status keeps the historical pull default`() {
        assertEquals("completed", RemoteStatusNormalizer.orderStatus(null))
        assertEquals("completed", RemoteStatusNormalizer.orderStatus("   "))
    }

    @Test
    fun `unknown order status falls back to the caller default`() {
        assertEquals("draft", RemoteStatusNormalizer.orderStatus("teleported", OrderStatus.DRAFT))
    }

    @Test
    fun `payment status spellings normalise`() {
        assertEquals("success", RemoteStatusNormalizer.paymentStatus("PAID"))
        assertEquals("success", RemoteStatusNormalizer.paymentStatus("Captured"))
        assertEquals("pending", RemoteStatusNormalizer.paymentStatus("Processing"))
        assertEquals("failed", RemoteStatusNormalizer.paymentStatus("DECLINED"))
        assertEquals("success", RemoteStatusNormalizer.paymentStatus(null))
    }

    @Test
    fun `payment mode accepts db values and display labels`() {
        assertEquals("cash", RemoteStatusNormalizer.paymentMode(null))
        assertEquals("upi", RemoteStatusNormalizer.paymentMode("UPI"))
        assertEquals("pos", RemoteStatusNormalizer.paymentMode("POS Machine"))
        assertEquals("easebuzz", RemoteStatusNormalizer.paymentMode("Pay Online"))
        assertEquals("part_cash_upi", RemoteStatusNormalizer.paymentMode("Cash + UPI"))
        assertEquals("part_upi_pos", RemoteStatusNormalizer.paymentMode("upi+pos"))
        // Unknown modes must not silently become a part-payment split.
        assertEquals("cash", RemoteStatusNormalizer.paymentMode("crypto"))
    }

    @Test
    fun `local enum readers understand the same aliases`() {
        assertEquals(OrderStatus.COMPLETED, OrderStatus.fromDbValue("PAID"))
        assertEquals(OrderStatus.CANCELLED, OrderStatus.fromDbValue("void"))
        assertEquals(PaymentStatus.SUCCESS, PaymentStatus.fromDbValue("Paid"))
        assertEquals(PaymentStatus.FAILED, PaymentStatus.fromDbValue("declined"))
        // Unknown still reads as DRAFT, so a typo cannot invent revenue locally.
        assertEquals(OrderStatus.DRAFT, OrderStatus.fromDbValue("something-else"))
    }
}
