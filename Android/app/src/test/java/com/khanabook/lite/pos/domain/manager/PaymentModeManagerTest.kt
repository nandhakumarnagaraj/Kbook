package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.domain.model.PaymentMode
import org.junit.Assert.assertEquals
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
}
