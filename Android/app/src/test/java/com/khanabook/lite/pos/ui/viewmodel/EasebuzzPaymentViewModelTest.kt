package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderResponse
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EasebuzzPaymentViewModelTest {

    private val paymentRepository: EasebuzzPaymentRepository = mockk(relaxed = true)
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.d(any(), any()) } returns 0
        io.mockk.every { android.util.Log.i(any(), any()) } returns 0
        io.mockk.every { android.util.Log.w(any(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.e(any(), any()) } returns 0

        mockkObject(KhanaToast)
        coEvery { KhanaToast.show(any(), any(), any(), any()) } returns androidx.compose.material3.SnackbarResult.Dismissed

        coEvery { paymentRepository.getPaymentStatus(any(), any()) } returns Result.success(mapOf("paymentStatus" to "pending"))
        coEvery { paymentRepository.verifyPayment(any()) } returns Result.success(mapOf("paymentStatus" to "pending"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(billId: Long = 101L, restaurantId: Long = 999L): EasebuzzPaymentViewModel {
        val savedStateHandle = SavedStateHandle(
            mapOf("billId" to billId, "restaurantId" to restaurantId)
        )
        return EasebuzzPaymentViewModel(paymentRepository, savedStateHandle)
    }

    @Test
    fun `createOrder with invalid ids sets Error state`() = runTest(testScheduler) {
        val viewModel = createViewModel(billId = 0L, restaurantId = 0L)

        viewModel.createOrder()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is EasebuzzPaymentState.Error)
        assertEquals("Invalid bill or restaurant ID", (state as EasebuzzPaymentState.Error).message)
    }

    @Test
    fun `createOrder success sets PaymentReady state`() = runTest(testScheduler) {
        val viewModel = createViewModel(billId = 101L, restaurantId = 999L)
        val response = CreateEasebuzzOrderResponse(
            status = "success",
            accessToken = "TOKEN_123",
            paymentUrl = "https://pay.easebuzz.in/pay/TOKEN_123",
            txnId = "KB0010100999ABCDEF12"
        )
        coEvery { paymentRepository.createOrder(101L, 999L) } returns Result.success(response)

        viewModel.createOrder()
        testScheduler.runCurrent()

        val state = viewModel.state.value
        assertTrue(state is EasebuzzPaymentState.PaymentReady)
        val ready = state as EasebuzzPaymentState.PaymentReady
        assertEquals("TOKEN_123", ready.accessToken)
        assertEquals("https://pay.easebuzz.in/pay/TOKEN_123", ready.paymentUrl)
        assertEquals("KB0010100999ABCDEF12", ready.txnId)
        assertEquals("KB0010100999ABCDEF12", viewModel.currentTxnId)
        assertEquals("https://pay.easebuzz.in/pay/TOKEN_123", viewModel.lastPaymentUrl)
        viewModel.reset()
    }

    @Test
    fun `createOrder failure sets Error state after retries`() = runTest(testScheduler) {
        val viewModel = createViewModel(billId = 101L, restaurantId = 999L)
        coEvery { paymentRepository.createOrder(101L, 999L) } returns Result.failure(RuntimeException("Gateway timeout"))

        viewModel.createOrder()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is EasebuzzPaymentState.Error)
        assertTrue((state as EasebuzzPaymentState.Error).message.contains("Gateway timeout"))
    }

    @Test
    fun `verifyAndComplete with paid status transitions to PaymentSuccess`() = runTest(testScheduler) {
        val viewModel = createViewModel(billId = 101L, restaurantId = 999L)
        coEvery { paymentRepository.getPaymentStatus(101L, refresh = true) } returns Result.success(
            mapOf("paymentStatus" to "paid", "gatewayTxnId" to "EASEBUZZ_GW_123")
        )
        coEvery { paymentRepository.verifyPayment(101L) } returns Result.success(
            mapOf("paymentStatus" to "paid", "gatewayTxnId" to "EASEBUZZ_GW_123")
        )

        viewModel.verifyAndComplete("SDK_TXN_001")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is EasebuzzPaymentState.PaymentSuccess)
        assertEquals("EASEBUZZ_GW_123", (state as EasebuzzPaymentState.PaymentSuccess).txnId)
    }

    @Test
    fun `verifyAndComplete with failed status transitions to PaymentFailed`() = runTest(testScheduler) {
        val viewModel = createViewModel(billId = 101L, restaurantId = 999L)
        coEvery { paymentRepository.getPaymentStatus(101L, refresh = true) } returns Result.success(
            mapOf("paymentStatus" to "pending")
        )
        coEvery { paymentRepository.verifyPayment(101L) } returns Result.success(
            mapOf("paymentStatus" to "failed")
        )

        viewModel.verifyAndComplete()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is EasebuzzPaymentState.PaymentFailed)
    }

    @Test
    fun `verifyPayment marks success when payment is paid`() = runTest(testScheduler) {
        val viewModel = createViewModel(billId = 101L, restaurantId = 999L)
        coEvery { paymentRepository.verifyPayment(101L) } returns Result.success(
            mapOf("paymentStatus" to "paid", "gatewayTxnId" to "GW_PAID_777")
        )

        viewModel.verifyPayment()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is EasebuzzPaymentState.PaymentSuccess)
        assertEquals("GW_PAID_777", (state as EasebuzzPaymentState.PaymentSuccess).txnId)
    }

    @Test
    fun `onSdkUnavailable sets Error state`() {
        val viewModel = createViewModel(billId = 101L, restaurantId = 999L)

        viewModel.onSdkUnavailable("Easebuzz SDK not found")

        val state = viewModel.state.value
        assertTrue(state is EasebuzzPaymentState.Error)
        assertEquals("Easebuzz SDK not found", (state as EasebuzzPaymentState.Error).message)
    }

    @Test
    fun `reset returns state to Idle and resets session timer`() {
        val viewModel = createViewModel(billId = 101L, restaurantId = 999L)
        viewModel.onSdkUnavailable("Temporary error")

        viewModel.reset()

        assertEquals(EasebuzzPaymentState.Idle, viewModel.state.value)
        assertEquals(300, viewModel.secondsLeft.value)
    }
}
