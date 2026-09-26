package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.payments.domain.PaymentComponent
import com.khanabook.lite.pos.feature.payments.domain.PaymentModeManager
import com.khanabook.lite.pos.feature.payments.domain.PaymentSetValidator

import com.khanabook.lite.pos.feature.billing.data.BillPaymentEntity
import com.khanabook.lite.pos.domain.model.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentModeManagerTest {
    @Test
    fun `all split modes produce two atomic payment components`() {
        val cases = listOf(
            PaymentMode.PART_CASH_UPI to listOf(PaymentMode.CASH, PaymentMode.UPI),
            PaymentMode.PART_CASH_POS to listOf(PaymentMode.CASH, PaymentMode.POS),
            PaymentMode.PART_UPI_POS to listOf(PaymentMode.UPI, PaymentMode.POS)
        )

        cases.forEach { (splitMode, expectedModes) ->
            val components = PaymentModeManager.getPaymentComponents(
                mode = splitMode,
                totalAmount = "100.00",
                partAmount1 = "40.00",
                partAmount2 = "60.00"
            )

            assertEquals(expectedModes, components.map { it.mode })
            assertEquals(listOf("40.00", "60.00"), components.map { it.amount })
            assertTrue(
                PaymentSetValidator.validate(
                    payments = components.map { component ->
                        BillPaymentEntity(
                            billId = 1,
                            paymentMode = component.mode.dbValue,
                            amount = component.amount,
                            operationId = "bill:payment:${component.mode.dbValue}"
                        )
                    },
                    payableTotal = "100.00"
                ).isSuccess
            )
        }
    }

    @Test
    fun `non split mode produces one component for the full total`() {
        val components = PaymentModeManager.getPaymentComponents(
            mode = PaymentMode.CASH,
            totalAmount = "100.00",
            partAmount1 = "40.00",
            partAmount2 = "60.00"
        )

        assertEquals(
            listOf(PaymentComponent(PaymentMode.CASH, "100.00")),
            components
        )
    }

    @Test
    fun `every single mode produces one component carrying the full total`() {
        listOf(
            PaymentMode.CASH,
            PaymentMode.UPI,
            PaymentMode.POS,
            PaymentMode.EASEBUZZ,
            PaymentMode.PAYMENT_LINK
        ).forEach { mode ->
            val components = PaymentModeManager.getPaymentComponents(
                mode = mode,
                totalAmount = "250.00",
                partAmount1 = "100.00",
                partAmount2 = "150.00"
            )

            assertEquals(1, components.size)
            assertEquals(mode, components[0].mode)
            assertEquals("250.00", components[0].amount)
        }
    }

    @Test
    fun `isPartPayment is true only for the three split modes`() {
        assertTrue(PaymentModeManager.isPartPayment(PaymentMode.PART_CASH_UPI))
        assertTrue(PaymentModeManager.isPartPayment(PaymentMode.PART_CASH_POS))
        assertTrue(PaymentModeManager.isPartPayment(PaymentMode.PART_UPI_POS))
        assertFalse(PaymentModeManager.isPartPayment(PaymentMode.CASH))
        assertFalse(PaymentModeManager.isPartPayment(PaymentMode.UPI))
        assertFalse(PaymentModeManager.isPartPayment(PaymentMode.POS))
        assertFalse(PaymentModeManager.isPartPayment(PaymentMode.EASEBUZZ))
        assertFalse(PaymentModeManager.isPartPayment(PaymentMode.PAYMENT_LINK))
    }

    @Test
    fun `getPartLabels returns the leg labels for each split mode`() {
        assertEquals("Cash" to "UPI", PaymentModeManager.getPartLabels(PaymentMode.PART_CASH_UPI))
        assertEquals("Cash" to "POS", PaymentModeManager.getPartLabels(PaymentMode.PART_CASH_POS))
        assertEquals("UPI" to "POS", PaymentModeManager.getPartLabels(PaymentMode.PART_UPI_POS))
        assertEquals("" to "", PaymentModeManager.getPartLabels(PaymentMode.CASH))
    }

    @Test
    fun `getDisplayLabel delegates to the mode display label`() {
        assertEquals("Cash", PaymentModeManager.getDisplayLabel(PaymentMode.CASH))
        assertEquals("UPI", PaymentModeManager.getDisplayLabel(PaymentMode.UPI))
        assertEquals("POS Machine", PaymentModeManager.getDisplayLabel(PaymentMode.POS))
        assertEquals("Pay Online", PaymentModeManager.getDisplayLabel(PaymentMode.EASEBUZZ))
        assertEquals("Send Payment Link", PaymentModeManager.getDisplayLabel(PaymentMode.PAYMENT_LINK))
        assertEquals("Cash + UPI", PaymentModeManager.getDisplayLabel(PaymentMode.PART_CASH_UPI))
        assertEquals("Cash + POS", PaymentModeManager.getDisplayLabel(PaymentMode.PART_CASH_POS))
        assertEquals("UPI + POS", PaymentModeManager.getDisplayLabel(PaymentMode.PART_UPI_POS))
    }
}
