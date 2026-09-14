package com.khanabook.lite.pos.ui.viewmodel
import com.khanabook.lite.pos.feature.billing.viewmodel.PaymentStateManager

import com.khanabook.lite.pos.domain.model.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PaymentStateManagerTest {

    private lateinit var manager: PaymentStateManager

    @Before
    fun setUp() {
        manager = PaymentStateManager()
    }

    @Test
    fun `buildPaymentEntities populates gateway fields when gateway result is present`() {
        manager.setPaymentMode(PaymentMode.EASEBUZZ)
        manager.setGatewayResult("TXN_EASEBUZZ_12345", "success")

        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.EASEBUZZ,
            totalAmount = "250.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(1, entities.size)
        val entity = entities[0]
        assertEquals(42L, entity.billId)
        assertEquals("easebuzz", entity.paymentMode)
        assertEquals("250.00", entity.amount)
        assertEquals("TXN_EASEBUZZ_12345", entity.gatewayTxnId)
        assertEquals("success", entity.gatewayStatus)
        assertEquals("gateway", entity.verifiedBy)
    }

    @Test
    fun `buildPaymentEntities leaves gateway fields null for manual cash payments`() {
        manager.setPaymentMode(PaymentMode.CASH)

        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.CASH,
            totalAmount = "150.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(1, entities.size)
        val entity = entities[0]
        assertEquals("cash", entity.paymentMode)
        assertEquals("150.00", entity.amount)
        assertNull(entity.gatewayTxnId)
        assertNull(entity.gatewayStatus)
        assertEquals("manual", entity.verifiedBy)
    }

    @Test
    fun `clearGatewayResult resets gateway fields for subsequent builds`() {
        manager.setGatewayResult("TXN_OLD", "success")
        manager.clearGatewayResult()

        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.UPI,
            totalAmount = "100.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(1, entities.size)
        assertNull(entities[0].gatewayTxnId)
        assertNull(entities[0].gatewayStatus)
        assertEquals("manual", entities[0].verifiedBy)
    }

    @Test
    fun `reset clears payment mode, amounts, and gateway result`() {
        manager.setPaymentMode(PaymentMode.EASEBUZZ)
        manager.setGatewayResult("TXN_123", "success")
        manager.setPersistedPaymentTotal("500.00")

        manager.reset()

        assertEquals(PaymentMode.UPI, manager.paymentMode.value)
        assertNull(manager.gatewayTxnId.value)
        assertNull(manager.gatewayStatus.value)
        assertNull(manager.persistedPaymentTotal.value)
    }
}