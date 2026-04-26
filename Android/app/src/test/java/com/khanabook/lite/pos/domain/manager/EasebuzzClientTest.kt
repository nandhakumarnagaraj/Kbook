package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.remote.api.CreateEasebuzzOrderResponse
import com.khanabook.lite.pos.data.remote.api.EasebuzzPaymentStatusResponse
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EasebuzzClientTest {

    @Test
    fun `initiateTxn maps backend response to InitResult Success`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.createEasebuzzOrder(any())).thenReturn(
            CreateEasebuzzOrderResponse(
                paymentId = 11L,
                billId = 7L,
                amount = "10.00",
                currency = "INR",
                gateway = "EASEBUZZ",
                gatewayTxnId = "T1",
                checkoutUrl = "https://pay.easebuzz.in/pay/abc"
            )
        )
        val client = EasebuzzClient(api)

        val result = client.initiateTxn(7L, "UPI")

        assertTrue(result is EasebuzzClient.InitResult.Success)
        val success = result as EasebuzzClient.InitResult.Success
        assertEquals(11L, success.paymentId)
        assertEquals(7L, success.billId)
        assertEquals("T1", success.txnId)
    }

    @Test
    fun `initiateTxn surfaces backend exception as Error`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.createEasebuzzOrder(any())).thenThrow(RuntimeException("boom"))
        val client = EasebuzzClient(api)

        val result = client.initiateTxn(7L, "UPI")

        assertTrue(result is EasebuzzClient.InitResult.Error)
        assertTrue((result as EasebuzzClient.InitResult.Error).message.contains("boom"))
    }

    @Test
    fun `verifyBill maps backend success to VerifyResult Success`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.getEasebuzzPaymentStatus(any())).thenReturn(
            EasebuzzPaymentStatusResponse(
                billId = 7L,
                paymentId = 11L,
                paymentStatus = "SUCCESS",
                gatewayTxnId = "T1",
                amount = "10.00",
                message = "Payment successful"
            )
        )
        val client = EasebuzzClient(api)

        val result = client.verifyBill(7L)

        assertTrue(result is EasebuzzClient.VerifyResult.Success)
        assertEquals("T1", (result as EasebuzzClient.VerifyResult.Success).txnId)
    }

    @Test
    fun `verifyBill maps backend failed to VerifyResult Failed`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.getEasebuzzPaymentStatus(any())).thenReturn(
            EasebuzzPaymentStatusResponse(
                billId = 7L,
                paymentId = 11L,
                paymentStatus = "FAILED",
                gatewayTxnId = "T2",
                amount = "10.00",
                message = "Gateway reported failure"
            )
        )
        val client = EasebuzzClient(api)

        val result = client.verifyBill(7L)

        assertTrue(result is EasebuzzClient.VerifyResult.Failed)
        assertEquals("Gateway reported failure", (result as EasebuzzClient.VerifyResult.Failed).reason)
    }

    @Test
    fun `verifyBill maps backend pending to VerifyResult Pending`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.getEasebuzzPaymentStatus(any())).thenReturn(
            EasebuzzPaymentStatusResponse(
                billId = 7L,
                paymentId = 11L,
                paymentStatus = "PENDING",
                gatewayTxnId = "T3",
                amount = "10.00",
                message = "Payment pending"
            )
        )
        val client = EasebuzzClient(api)

        val result = client.verifyBill(7L)

        assertTrue(result is EasebuzzClient.VerifyResult.Pending)
    }

    @Test
    fun `verifyBill maps unknown status to VerifyResult Error`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.getEasebuzzPaymentStatus(any())).thenReturn(
            EasebuzzPaymentStatusResponse(
                billId = 7L,
                paymentId = 11L,
                paymentStatus = "GARBAGE",
                gatewayTxnId = "T4",
                amount = "10.00",
                message = "Unknown"
            )
        )
        val client = EasebuzzClient(api)

        val result = client.verifyBill(7L)

        assertTrue(result is EasebuzzClient.VerifyResult.Error)
    }

    @Test
    fun `verifyBill surfaces network exception as Error`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.getEasebuzzPaymentStatus(any())).thenThrow(RuntimeException("boom"))
        val client = EasebuzzClient(api)

        val result = client.verifyBill(7L)

        assertTrue(result is EasebuzzClient.VerifyResult.Error)
        assertTrue((result as EasebuzzClient.VerifyResult.Error).message.contains("boom"))
    }
}
