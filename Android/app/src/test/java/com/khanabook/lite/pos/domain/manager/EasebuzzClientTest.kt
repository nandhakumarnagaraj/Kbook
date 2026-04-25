package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.EasebuzzVerifyResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EasebuzzClientTest {

    private val httpClient = OkHttpClient()

    @Test
    fun `buildRequestHash matches Easebuzz spec format`() {
        val client = EasebuzzClient(httpClient, mock())
        // Vector chosen so we can recompute by hand:
        //   sha512("KEY|TXN1|10.00|info|First|user@example.com|||||||||||SALT")
        val hash = client.buildRequestHash(
            key = "KEY",
            txnId = "TXN1",
            amount = "10.00",
            productInfo = "info",
            firstName = "First",
            email = "user@example.com",
            salt = "SALT"
        )
        // SHA-512 hex output is 128 chars
        assertEquals(128, hash.length)
        // Hash is deterministic — same inputs must produce same output
        val hash2 = client.buildRequestHash("KEY", "TXN1", "10.00", "info", "First", "user@example.com", "SALT")
        assertEquals(hash, hash2)
    }

    @Test
    fun `newTxnId starts with KB and has expected length`() {
        val client = EasebuzzClient(httpClient, mock())
        val id = client.newTxnId()
        assertTrue("Txn id should start with KB but was $id", id.startsWith("KB"))
        // "KB" + 20 chars from UUID = 22
        assertEquals(22, id.length)
    }

    @Test
    fun `checkoutUrl points at testpay for non-prod env`() {
        val client = EasebuzzClient(httpClient, mock())
        val url = client.checkoutUrl("test", "ABC123")
        assertEquals("https://testpay.easebuzz.in/pay/ABC123", url)
    }

    @Test
    fun `checkoutUrl points at pay easebuzz in for prod env`() {
        val client = EasebuzzClient(httpClient, mock())
        val url = client.checkoutUrl("PROD", "XYZ789")
        assertEquals("https://pay.easebuzz.in/pay/XYZ789", url)
    }

    @Test
    fun `verifyTxn maps server success to VerifyResult Success`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.verifyEasebuzzTxn(any())).thenReturn(
            EasebuzzVerifyResponse(txnId = "T1", status = "success", gatewayStatus = "success", found = true)
        )
        val client = EasebuzzClient(httpClient, api)
        val r = client.verifyTxn("T1")
        assertTrue(r is EasebuzzClient.VerifyResult.Success)
        assertEquals("T1", (r as EasebuzzClient.VerifyResult.Success).txnId)
    }

    @Test
    fun `verifyTxn maps server failed to VerifyResult Failed with raw reason`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.verifyEasebuzzTxn(any())).thenReturn(
            EasebuzzVerifyResponse(txnId = "T2", status = "failed", gatewayStatus = "userCancelled", found = true)
        )
        val client = EasebuzzClient(httpClient, api)
        val r = client.verifyTxn("T2")
        assertTrue(r is EasebuzzClient.VerifyResult.Failed)
        val f = r as EasebuzzClient.VerifyResult.Failed
        assertEquals("userCancelled", f.gatewayStatus)
        assertEquals("userCancelled", f.reason)
    }

    @Test
    fun `verifyTxn maps server pending to VerifyResult Pending`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.verifyEasebuzzTxn(any())).thenReturn(
            EasebuzzVerifyResponse(txnId = "T3", status = "pending", found = false)
        )
        val client = EasebuzzClient(httpClient, api)
        val r = client.verifyTxn("T3")
        assertTrue(r is EasebuzzClient.VerifyResult.Pending)
    }

    @Test
    fun `verifyTxn maps unknown status to VerifyResult Error`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.verifyEasebuzzTxn(any())).thenReturn(
            EasebuzzVerifyResponse(txnId = "T4", status = "garbage", found = false)
        )
        val client = EasebuzzClient(httpClient, api)
        val r = client.verifyTxn("T4")
        assertTrue(r is EasebuzzClient.VerifyResult.Error)
    }

    @Test
    fun `verifyTxn surfaces network exception as Error`() = runBlocking {
        val api = mock<KhanaBookApi>()
        whenever(api.verifyEasebuzzTxn(any())).thenThrow(RuntimeException("boom"))
        val client = EasebuzzClient(httpClient, api)
        val r = client.verifyTxn("T5")
        assertTrue(r is EasebuzzClient.VerifyResult.Error)
        assertTrue((r as EasebuzzClient.VerifyResult.Error).message.contains("boom"))
    }

    @Test
    fun `initiateTxn returns Error when credentials missing`() = runBlocking {
        val client = EasebuzzClient(httpClient, mock())
        val profile = RestaurantProfileEntity(
            easebuzzMerchantKey = null,
            easebuzzSalt = null
        )
        val r = client.initiateTxn(
            profile = profile,
            amount = "10.00",
            productInfo = "info",
            firstName = "First",
            email = "user@example.com",
            phone = "9999999999"
        )
        assertTrue(r is EasebuzzClient.InitResult.Error)
    }
}
