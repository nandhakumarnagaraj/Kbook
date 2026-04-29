package com.khanabook.lite.pos.data.remote.dto

import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncEntityMappersTest {

    @Test
    fun `bill payment mapper preserves gateway metadata`() {
        val entity = BillPaymentEntity(
            id = 7L,
            billId = 101L,
            paymentMode = "upi",
            amount = "262.50",
            createdAt = 1000L,
            restaurantId = 55L,
            deviceId = "device-1",
            updatedAt = 2000L,
            serverId = 88L,
            serverBillId = 99L,
            serverUpdatedAt = 3000L,
            gatewayTxnId = "TXN-123",
            gatewayStatus = "SUCCESS",
            verifiedBy = "manual"
        )

        val dto = entity.toSyncDto()

        assertEquals("TXN-123", dto.gatewayTxnId)
        assertEquals("SUCCESS", dto.gatewayStatus)
        assertEquals("manual", dto.verifiedBy)
        assertEquals("262.50", dto.amount)
        assertEquals(101L, dto.billId)
        assertEquals(99L, dto.serverBillId)
    }

    @Test
    fun `bill payment mapper keeps manual payments unverified`() {
        val entity = BillPaymentEntity(
            id = 9L,
            billId = 202L,
            paymentMode = "cash",
            amount = "100.00",
            verifiedBy = "manual"
        )

        val dto = entity.toSyncDto()

        assertNull(dto.gatewayTxnId)
        assertNull(dto.gatewayStatus)
        assertEquals("manual", dto.verifiedBy)
    }
}
