package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.feature.billing.viewmodel.PaymentStateManager
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.feature.payments.domain.PaymentLimits
import com.khanabook.lite.pos.feature.payments.domain.PaymentRecoveryAssessment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class PaymentStateManagerTest {

    private lateinit var manager: PaymentStateManager

    @Before
    fun setUp() {
        manager = PaymentStateManager()
    }

    // ── buildPaymentEntities: gateway stamping ──────────────────────────────

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
    fun `buildPaymentEntities marks UPI as gateway-verified when a gateway result is present`() {
        manager.setPaymentMode(PaymentMode.UPI)
        manager.setGatewayResult("TXN_UPI_999", "success")

        val entities = manager.buildPaymentEntities(
            billId = 7L,
            paymentMode = PaymentMode.UPI,
            totalAmount = "500.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(1, entities.size)
        assertEquals("upi", entities[0].paymentMode)
        assertEquals("TXN_UPI_999", entities[0].gatewayTxnId)
        assertEquals("success", entities[0].gatewayStatus)
        assertEquals("gateway", entities[0].verifiedBy)
    }

    @Test
    fun `buildPaymentEntities defaults gateway status to success when gateway status is null`() {
        manager.setPaymentMode(PaymentMode.EASEBUZZ)
        manager.setGatewayResult("TXN_EB_1", null)

        val entities = manager.buildPaymentEntities(
            billId = 7L,
            paymentMode = PaymentMode.EASEBUZZ,
            totalAmount = "250.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(1, entities.size)
        assertEquals("TXN_EB_1", entities[0].gatewayTxnId)
        assertEquals("success", entities[0].gatewayStatus)
        assertEquals("gateway", entities[0].verifiedBy)
    }

    @Test
    fun `buildPaymentEntities for easebuzz without a gateway result keeps verifiedBy manual`() {
        manager.setPaymentMode(PaymentMode.EASEBUZZ)

        val entities = manager.buildPaymentEntities(
            billId = 7L,
            paymentMode = PaymentMode.EASEBUZZ,
            totalAmount = "250.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(1, entities.size)
        assertNull(entities[0].gatewayTxnId)
        assertEquals("success", entities[0].gatewayStatus)
        assertEquals("manual", entities[0].verifiedBy)
    }

    // ── validatePaymentLimits: UPI single-transaction cap ───────────────────

    @Test
    fun `validatePaymentLimits returns message when UPI total exceeds the cap`() {
        val overLimit = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.add(BigDecimal("0.01")).toPlainString()

        val error = manager.validatePaymentLimits(total = overLimit, mode = PaymentMode.UPI)

        assertEquals(PaymentLimits.UPI_LIMIT_MESSAGE, error)
    }

    @Test
    fun `validatePaymentLimits returns null when UPI total equals the cap`() {
        val atLimit = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.toPlainString()

        assertNull(manager.validatePaymentLimits(total = atLimit, mode = PaymentMode.UPI))
    }

    @Test
    fun `validatePaymentLimits checks the UPI leg partAmount2 for PART_CASH_UPI`() {
        val overLimit = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.add(BigDecimal("1")).toPlainString()

        val error = manager.validatePaymentLimits(
            total = "250000.00",
            mode = PaymentMode.PART_CASH_UPI,
            partAmount1 = "150000.00",
            partAmount2 = overLimit
        )

        assertEquals(PaymentLimits.UPI_LIMIT_MESSAGE, error)
    }

    @Test
    fun `validatePaymentLimits ignores the cash leg for PART_CASH_UPI`() {
        val atLimit = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.toPlainString()

        assertNull(
            manager.validatePaymentLimits(
                total = "250000.00",
                mode = PaymentMode.PART_CASH_UPI,
                partAmount1 = "150000.00",
                partAmount2 = atLimit
            )
        )
    }

    @Test
    fun `validatePaymentLimits checks the UPI leg partAmount1 for PART_UPI_POS`() {
        val overLimit = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.add(BigDecimal("1")).toPlainString()

        val error = manager.validatePaymentLimits(
            total = "250000.00",
            mode = PaymentMode.PART_UPI_POS,
            partAmount1 = overLimit,
            partAmount2 = "150000.00"
        )

        assertEquals(PaymentLimits.UPI_LIMIT_MESSAGE, error)
    }

    @Test
    fun `validatePaymentLimits never limits cash mode`() {
        val huge = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.multiply(BigDecimal("10")).toPlainString()

        assertNull(manager.validatePaymentLimits(total = huge, mode = PaymentMode.CASH))
    }

    @Test
    fun `validatePaymentLimits falls back to state defaults when args omitted`() {
        manager.setPaymentMode(PaymentMode.UPI)
        val overLimit = PaymentLimits.UPI_SINGLE_TRANSACTION_MAX.add(BigDecimal("1")).toPlainString()

        assertEquals(PaymentLimits.UPI_LIMIT_MESSAGE, manager.validatePaymentLimits(overLimit))
    }

    // ── setPaymentMode: gateway-result semantics ────────────────────────────

    @Test
    fun `setPaymentMode to cash clears a stale gateway result`() {
        manager.setGatewayResult("TXN_OLD", "success")

        manager.setPaymentMode(PaymentMode.CASH)

        assertNull(manager.gatewayTxnId.value)
        assertNull(manager.gatewayStatus.value)
    }

    @Test
    fun `setPaymentMode to easebuzz clears a stale gateway result`() {
        manager.setGatewayResult("TXN_OLD", "success")

        manager.setPaymentMode(PaymentMode.EASEBUZZ)

        assertNull(manager.gatewayTxnId.value)
        assertNull(manager.gatewayStatus.value)
    }

    @Test
    fun `setPaymentMode to PART_CASH_POS clears a stale gateway result`() {
        manager.setGatewayResult("TXN_OLD", "success")

        manager.setPaymentMode(PaymentMode.PART_CASH_POS, "100.0", "150.0")

        assertNull(manager.gatewayTxnId.value)
        assertNull(manager.gatewayStatus.value)
    }

    @Test
    fun `setPaymentMode to UPI preserves the gateway result`() {
        manager.setGatewayResult("TXN_UPI", "success")

        manager.setPaymentMode(PaymentMode.UPI)

        assertEquals("TXN_UPI", manager.gatewayTxnId.value)
        assertEquals("success", manager.gatewayStatus.value)
    }

    @Test
    fun `setPaymentMode to PART_CASH_UPI preserves the gateway result and sets split amounts`() {
        manager.setGatewayResult("TXN_SPLIT", "success")

        manager.setPaymentMode(PaymentMode.PART_CASH_UPI, "100.0", "150.0")

        assertEquals("TXN_SPLIT", manager.gatewayTxnId.value)
        assertEquals("100.0", manager.partAmount1.value)
        assertEquals("150.0", manager.partAmount2.value)
    }

    @Test
    fun `setPaymentMode to PART_UPI_POS preserves the gateway result`() {
        manager.setGatewayResult("TXN_SPLIT", "success")

        manager.setPaymentMode(PaymentMode.PART_UPI_POS, "200.0", "100.0")

        assertEquals("TXN_SPLIT", manager.gatewayTxnId.value)
        assertEquals("200.0", manager.partAmount1.value)
        assertEquals("100.0", manager.partAmount2.value)
    }

    @Test
    fun `setPaymentMode normalizes blank split amounts to zero`() {
        manager.setPaymentMode(PaymentMode.PART_CASH_UPI, "", "")

        assertEquals("0.0", manager.partAmount1.value)
        assertEquals("0.0", manager.partAmount2.value)
    }

    // ── Other state holders ─────────────────────────────────────────────────

    @Test
    fun `setPaymentModeOnly changes mode without touching amounts or gateway result`() {
        manager.setPaymentMode(PaymentMode.PART_CASH_UPI, "100.0", "150.0")
        manager.setGatewayResult("TXN_SPLIT", "success")

        manager.setPaymentModeOnly(PaymentMode.CASH)

        assertEquals(PaymentMode.CASH, manager.paymentMode.value)
        assertEquals("100.0", manager.partAmount1.value)
        assertEquals("150.0", manager.partAmount2.value)
        assertEquals("TXN_SPLIT", manager.gatewayTxnId.value)
    }

    @Test
    fun `setPartAmounts updates both legs`() {
        manager.setPartAmounts("120.50", "80.50")

        assertEquals("120.50", manager.partAmount1.value)
        assertEquals("80.50", manager.partAmount2.value)
    }

    @Test
    fun `setPersistedPaymentTotal stores and clears the draft total`() {
        manager.setPersistedPaymentTotal("236.00")

        assertEquals("236.00", manager.persistedPaymentTotal.value)

        manager.setPersistedPaymentTotal(null)

        assertNull(manager.persistedPaymentTotal.value)
    }

    @Test
    fun `setPaymentRecovery stores the assessment`() {
        manager.setPaymentRecovery(PaymentRecoveryAssessment.Empty)

        assertEquals(PaymentRecoveryAssessment.Empty, manager.paymentRecovery.value)
    }

    // ── buildPaymentEntities: part payments ─────────────────────────────────

    @Test
    fun `buildPaymentEntities with PART_CASH_UPI produces cash and upi components`() {
        manager.setPaymentMode(PaymentMode.PART_CASH_UPI, "100.00", "150.00")

        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.PART_CASH_UPI,
            totalAmount = "250.00",
            partAmount1 = "100.00",
            partAmount2 = "150.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(2, entities.size)
        val cash = entities[0]
        val upi = entities[1]
        assertEquals("cash", cash.paymentMode)
        assertEquals("100.00", cash.amount)
        assertEquals("rest1:termA:tokenX:payment:cash", cash.operationId)
        assertEquals("upi", upi.paymentMode)
        assertEquals("150.00", upi.amount)
        assertEquals("rest1:termA:tokenX:payment:upi", upi.operationId)
    }

    @Test
    fun `buildPaymentEntities with PART_CASH_UPI stamps only the UPI leg with the gateway txn ID`() {
        manager.setPaymentMode(PaymentMode.PART_CASH_UPI, "100.00", "150.00")
        manager.setGatewayResult("TXN_EB_UPI", "success")

        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.PART_CASH_UPI,
            totalAmount = "250.00",
            partAmount1 = "100.00",
            partAmount2 = "150.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(2, entities.size)
        val cash = entities.first { it.paymentMode == "cash" }
        val upi = entities.first { it.paymentMode == "upi" }
        assertNull(cash.gatewayTxnId)
        assertNull(cash.gatewayStatus)
        assertEquals("manual", cash.verifiedBy)
        assertEquals("TXN_EB_UPI", upi.gatewayTxnId)
        assertEquals("success", upi.gatewayStatus)
        assertEquals("gateway", upi.verifiedBy)
    }

    @Test
    fun `buildPaymentEntities with PART_CASH_UPI and no gateway result leaves both legs manual`() {
        manager.setPaymentMode(PaymentMode.PART_CASH_UPI, "100.00", "150.00")

        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.PART_CASH_UPI,
            totalAmount = "250.00",
            partAmount1 = "100.00",
            partAmount2 = "150.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(2, entities.size)
        entities.forEach { entity ->
            assertNull(entity.gatewayTxnId)
            assertNull(entity.gatewayStatus)
            assertEquals("manual", entity.verifiedBy)
        }
    }

    @Test
    fun `buildPaymentEntities with PART_CASH_UPI falls back to state split amounts when args omitted`() {
        manager.setPaymentMode(PaymentMode.PART_CASH_UPI, "80.00", "170.00")

        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.PART_CASH_UPI,
            totalAmount = "250.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(2, entities.size)
        assertEquals("80.00", entities[0].amount)
        assertEquals("170.00", entities[1].amount)
    }

    @Test
    fun `buildPaymentEntities with PART_UPI_POS produces upi and pos components`() {
        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.PART_UPI_POS,
            totalAmount = "300.00",
            partAmount1 = "200.00",
            partAmount2 = "100.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(2, entities.size)
        assertEquals("upi", entities[0].paymentMode)
        assertEquals("200.00", entities[0].amount)
        assertEquals("pos", entities[1].paymentMode)
        assertEquals("100.00", entities[1].amount)
    }

    @Test
    fun `buildPaymentEntities with PART_CASH_POS produces cash and pos components`() {
        val entities = manager.buildPaymentEntities(
            billId = 42L,
            paymentMode = PaymentMode.PART_CASH_POS,
            totalAmount = "300.00",
            partAmount1 = "200.00",
            partAmount2 = "100.00",
            operationBase = "rest1:termA:tokenX"
        )

        assertEquals(2, entities.size)
        assertEquals("cash", entities[0].paymentMode)
        assertEquals("200.00", entities[0].amount)
        assertEquals("pos", entities[1].paymentMode)
        assertEquals("100.00", entities[1].amount)
    }

    // ── buildPaymentEntities: identity propagation ──────────────────────────

    @Test
    fun `buildPaymentEntities propagates deviceId, restaurantId and operationId format`() {
        manager.setPaymentMode(PaymentMode.CASH)

        val entities = manager.buildPaymentEntities(
            billId = 9L,
            paymentMode = PaymentMode.CASH,
            totalAmount = "100.00",
            operationBase = "rest1:termA:tokenX",
            deviceId = "dev_abc123",
            restaurantId = 42L
        )

        assertEquals(1, entities.size)
        assertEquals("dev_abc123", entities[0].deviceId)
        assertEquals(42L, entities[0].restaurantId)
        assertEquals("rest1:termA:tokenX:payment:cash", entities[0].operationId)
    }

    // ── reset / clearGatewayResult ──────────────────────────────────────────

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
