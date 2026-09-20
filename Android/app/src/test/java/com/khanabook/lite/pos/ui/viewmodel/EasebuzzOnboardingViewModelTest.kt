package com.khanabook.lite.pos.ui.viewmodel
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingStep
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingUiState
import com.khanabook.lite.pos.feature.payments.data.EasebuzzKycAccessKeyResponse
import com.khanabook.lite.pos.feature.payments.viewmodel.EasebuzzOnboardingViewModel

import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingRepository
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EasebuzzOnboardingViewModelTest {

    private val onboardingRepository: EasebuzzOnboardingRepository = mockk(relaxed = true)
    private val restaurantRepository: RestaurantRepository = mockk(relaxed = true)
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        every { restaurantRepository.getProfileFlow() } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): EasebuzzOnboardingViewModel {
        return EasebuzzOnboardingViewModel(onboardingRepository, restaurantRepository)
    }

    @Test
    fun `PAN and IFSC validation regexes test accurately`() {
        assertTrue(EasebuzzOnboardingViewModel.isValidPan("ABCDE1234F"))
        assertTrue(EasebuzzOnboardingViewModel.isValidPan("AAAPA1234A"))
        assertFalse(EasebuzzOnboardingViewModel.isValidPan("abcde1234f"))
        assertFalse(EasebuzzOnboardingViewModel.isValidPan("12345ABCDE"))
        assertFalse(EasebuzzOnboardingViewModel.isValidPan("ABCD12345E"))

        assertTrue(EasebuzzOnboardingViewModel.isValidIfsc("HDFC0001234"))
        assertTrue(EasebuzzOnboardingViewModel.isValidIfsc("SBIN0000001"))
        assertFalse(EasebuzzOnboardingViewModel.isValidIfsc("hdfc0001234"))
        assertFalse(EasebuzzOnboardingViewModel.isValidIfsc("HDFC1234567"))
        assertFalse(EasebuzzOnboardingViewModel.isValidIfsc("HDFC000123"))
    }

    @Test
    fun `loadStatus sets Active when submerchant is active`() = runTest(testScheduler) {
        val activeStatus = EasebuzzOnboardingStatusResponse(
            hasSubMerchant = true,
            status = "ACTIVE",
            isActive = true
        )
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(activeStatus)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is OnboardingUiState.Active)
        assertEquals(activeStatus, (state as OnboardingUiState.Active).status)
    }

    @Test
    fun `loadStatus sets AwaitingKyc when submerchant is pending kyc`() = runTest(testScheduler) {
        val pendingStatus = EasebuzzOnboardingStatusResponse(
            hasSubMerchant = true,
            status = "PENDING_KYC",
            isActive = false
        )
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(pendingStatus)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is OnboardingUiState.AwaitingKyc)
    }

    @Test
    fun `loadStatus sets Rejected when submerchant status is REJECTED`() = runTest(testScheduler) {
        val rejectedStatus = EasebuzzOnboardingStatusResponse(
            hasSubMerchant = true,
            status = "REJECTED",
            isActive = false
        )
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(rejectedStatus)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is OnboardingUiState.Rejected)
    }

    @Test
    fun `loadStatus does not claim onboarding is needed when status lookup fails`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.failure(RuntimeException("Network error"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OnboardingUiState.Error)
    }

    @Test
    fun `navigation flow steps forward and backward correctly`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "NOT_STARTED", hasSubMerchant = false)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Start onboarding
        viewModel.startOnboarding()
        assertEquals(OnboardingStep.BusinessDetails, viewModel.currentStep.value)
        assertTrue(viewModel.uiState.value is OnboardingUiState.InProgress)

        // Cannot go back from first step
        assertFalse(viewModel.goBack())

        // Move to Bank Details
        viewModel.submitBusinessDetails()
        assertEquals(OnboardingStep.BankDetails, viewModel.currentStep.value)

        // Go back from Bank Details returns to Business Details
        assertTrue(viewModel.goBack())
        assertEquals(OnboardingStep.BusinessDetails, viewModel.currentStep.value)
    }

    @Test
    fun `submitBankDetailsAndOnboard succeeds and moves to OtpVerification`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "NOT_STARTED", hasSubMerchant = false)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startOnboarding()
        viewModel.businessName = "Test Kitchen"
        viewModel.pan = "ABCDE1234F"
        viewModel.businessAddress = "123 Main St"
        viewModel.state = "Karnataka"
        viewModel.contactEmail = "kitchen@example.com"
        viewModel.contactPhone = "9876543210"
        viewModel.bankAccountNo = "1234567890"
        viewModel.ifsc = "HDFC0001234"
        viewModel.bankName = "HDFC Bank"
        viewModel.branchName = "MG Road"
        viewModel.beneficiaryName = "Test Owner"

        val response = EasebuzzOnboardingResponse(
            status = "success",
            message = "Onboarding initiated"
        )
        coEvery { onboardingRepository.onboard(any()) } returns Result.success(response)

        viewModel.submitBankDetailsAndOnboard()
        advanceUntilIdle()

        assertEquals(OnboardingStep.OtpVerification, viewModel.currentStep.value)
    }

    @Test
    fun `verifyOtp succeeds and triggers KYC access key generation`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "PENDING_KYC", hasSubMerchant = true)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.goToStep(OnboardingStep.OtpVerification)
        coEvery { onboardingRepository.verifyOtp("123456") } returns Result.success(mapOf("status" to "success"))
        coEvery { onboardingRepository.generateKycAccessKey() } returns Result.success(
            com.khanabook.lite.pos.feature.payments.data.EasebuzzKycAccessKeyResponse(status = "success", kycUrl = "https://kyc.easebuzz.in/portal/test")
        )

        viewModel.verifyOtp("123456")
        advanceUntilIdle()

        assertEquals(OnboardingStep.KycStatus, viewModel.currentStep.value)
    }

    @Test
    fun `loadStatus with CPV_PENDING transitions to AwaitingKyc state`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "CPV_PENDING", hasSubMerchant = true)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is OnboardingUiState.AwaitingKyc)
    }
}
