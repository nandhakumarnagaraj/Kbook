package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.CreatePaymentLinkForBillRequest
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EasebuzzPaymentRepositoryTest {

    private val api: KhanaBookApi = mockk(relaxed = true)
    private lateinit var repository: EasebuzzPaymentRepository

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.d(any(), any()) } returns 0
        io.mockk.every { android.util.Log.i(any(), any()) } returns 0
        io.mockk.every { android.util.Log.w(any(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.e(any(), any()) } returns 0

        repository = EasebuzzPaymentRepository(api)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `createPaymentLinkForBill passes customerPhone and customerEmail to api`() = runTest {
        val requestSlot = slot<CreatePaymentLinkForBillRequest>()
        coEvery { api.createPaymentLinkForBill(capture(requestSlot)) } returns mapOf(
            "status" to "success",
            "payment_url" to "https://pay.easebuzz.in/link/123",
            "merchant_txn" to "PL12345ABCD"
        )

        val result = repository.createPaymentLinkForBill(
            billId = 101L,
            restaurantId = 5L,
            customerPhone = "9876543210",
            customerEmail = "guest@example.com"
        )

        assertTrue(result.isSuccess)
        val captured = requestSlot.captured
        assertEquals(101L, captured.billId)
        assertEquals(5L, captured.restaurantId)
        assertEquals("9876543210", captured.customerPhone)
        assertEquals("guest@example.com", captured.customerEmail)
    }

    @Test
    fun `createPaymentLinkForBill handles null customer phone and email`() = runTest {
        val requestSlot = slot<CreatePaymentLinkForBillRequest>()
        coEvery { api.createPaymentLinkForBill(capture(requestSlot)) } returns mapOf(
            "status" to "success",
            "payment_url" to "https://pay.easebuzz.in/link/456",
            "merchant_txn" to "PL67890XYZ"
        )

        val result = repository.createPaymentLinkForBill(
            billId = 202L,
            restaurantId = 9L
        )

        assertTrue(result.isSuccess)
        val captured = requestSlot.captured
        assertEquals(202L, captured.billId)
        assertEquals(9L, captured.restaurantId)
        assertEquals(null, captured.customerPhone)
        assertEquals(null, captured.customerEmail)
    }
}
