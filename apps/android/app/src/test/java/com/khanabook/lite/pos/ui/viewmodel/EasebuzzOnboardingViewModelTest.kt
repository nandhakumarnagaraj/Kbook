package com.khanabook.lite.pos.ui.viewmodel
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingEvent
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingStep
import com.khanabook.lite.pos.feature.payments.viewmodel.OnboardingUiState
import com.khanabook.lite.pos.feature.payments.data.EasebuzzKycAccessKeyResponse
import com.khanabook.lite.pos.feature.payments.viewmodel.EasebuzzOnboardingViewModel

import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingRepository
import com.khanabook.lite.pos.feature.payments.data.EasebuzzPaymentReadiness
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    fun `loadStatus sets Expired when KYC has lapsed`() = runTest(testScheduler) {
        val expiredStatus = EasebuzzOnboardingStatusResponse(
            hasSubMerchant = true,
            status = "KYC_EXPIRED",
            isActive = false
        )
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(expiredStatus)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Must not fall through to AwaitingKyc: a lapsed KYC never resolves on its own, because
        // Easebuzz sends no expiry webhook.
        assertTrue(viewModel.uiState.value is OnboardingUiState.Expired)
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
    fun `submitBankDetailsAndOnboard opens the portal it was handed, with no OTP stop`() = runTest(testScheduler) {
        // First read: nothing registered yet. Second read (after submission): Easebuzz holds the
        // sub-merchant and KYC is pending, which is what the KYC screen renders.
        coEvery { onboardingRepository.getOnboardingStatus() } returnsMany listOf(
            Result.success(EasebuzzOnboardingStatusResponse(status = "NOT_STARTED", hasSubMerchant = false)),
            Result.success(
                EasebuzzOnboardingStatusResponse(
                    status = "PENDING_KYC", hasSubMerchant = true, subMerchantId = "SM_TEST",
                    kycUrl = "https://kyc.easebuzz.in/portal/from-onboard"
                )
            )
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
            message = "Onboarding initiated",
            kycUrl = "https://kyc.easebuzz.in/portal/from-onboard"
        )
        coEvery { onboardingRepository.onboard(any()) } returns Result.success(response)

        viewModel.submitBankDetailsAndOnboard()
        advanceUntilIdle()

        // Easebuzz's create contract has no OTP field; the portal link is the next step.
        assertEquals(OnboardingStep.KycStatus, viewModel.currentStep.value)
        coVerify(exactly = 1) { onboardingRepository.onboard(any()) }
        coVerify(exactly = 0) { onboardingRepository.resubmit(any()) }
        coVerify(exactly = 0) { onboardingRepository.generateKycAccessKey() }
    }

    @Test
    fun `submitBankDetailsAndOnboard issues a portal link when the response carries none`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returnsMany listOf(
            Result.success(EasebuzzOnboardingStatusResponse(status = "NOT_STARTED", hasSubMerchant = false)),
            Result.success(
                EasebuzzOnboardingStatusResponse(
                    status = "PENDING_KYC", hasSubMerchant = true, subMerchantId = "SM_TEST"
                )
            )
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

        coEvery { onboardingRepository.onboard(any()) } returns Result.success(
            EasebuzzOnboardingResponse(status = "success", message = "Onboarding initiated")
        )
        coEvery { onboardingRepository.generateKycAccessKey() } returns Result.success(
            EasebuzzKycAccessKeyResponse(status = "success", kycUrl = "https://kyc.easebuzz.in/portal/refreshed")
        )

        viewModel.submitBankDetailsAndOnboard()
        advanceUntilIdle()

        assertEquals(OnboardingStep.KycStatus, viewModel.currentStep.value)
        coVerify(exactly = 1) { onboardingRepository.generateKycAccessKey() }
    }

    @Test
    fun `rejected merchant resubmits through resubmit, not onboard`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "REJECTED", hasSubMerchant = true)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        // The rejection screen starts the edit flow, which must end on /resubmit: a REJECTED
        // sub-merchant already exists at Easebuzz and /onboard refuses it.
        viewModel.resubmitAfterRejection()
        viewModel.businessName = "Test Kitchen"
        viewModel.bankAccountNo = "1234567890"
        coEvery { onboardingRepository.resubmit(any()) } returns Result.success(
            EasebuzzOnboardingResponse(status = "success", message = "resubmitted")
        )

        viewModel.submitBankDetailsAndOnboard()
        advanceUntilIdle()

        coVerify(exactly = 1) { onboardingRepository.resubmit(any()) }
        coVerify(exactly = 0) { onboardingRepository.onboard(any()) }
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

    @Test
    fun `loadPaymentReadiness sets Ready when repository succeeds`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "ACTIVE", hasSubMerchant = true, isActive = true)
        )
        val readiness = com.khanabook.lite.pos.feature.payments.data.EasebuzzPaymentReadiness(
            paymentLinkReady = true,
            easebuzzEnabled = true,
            subMerchantActive = true,
            subMerchantStatus = "ACTIVE"
        )
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.success(readiness)

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        val state = viewModel.paymentReadiness.value
        assertTrue(state is com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState.Ready)
        assertEquals(readiness, (state as com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState.Ready).readiness)
    }

    @Test
    fun `loadPaymentReadiness sets Unavailable with message when repository fails`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "ACTIVE", hasSubMerchant = true, isActive = true)
        )
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.failure(
            java.net.SocketTimeoutException("Unable to resolve host")
        )

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        val state = viewModel.paymentReadiness.value
        assertTrue(state is com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState.Unavailable)
        assertEquals(
            "Network error. Please check your connection.",
            (state as com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState.Unavailable).message
        )
    }

    @Test
    fun `loadPaymentReadiness sets Unavailable with fallback when exception has blank message`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            EasebuzzOnboardingStatusResponse(status = "ACTIVE", hasSubMerchant = true, isActive = true)
        )
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.failure(RuntimeException())

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        val state = viewModel.paymentReadiness.value
        assertTrue(state is com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState.Unavailable)
        val message = (state as com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState.Unavailable).message
        assertFalse(message.isNullOrBlank())
    }

    // ── The readiness response is the server's word on the Easebuzz switch ────────────────
    //
    // The server row owns the switch (owner-gated, confirmed there before it is stored) and the
    // till's copy only mirrors it. The mirror can lag - a web-admin change, or a till whose
    // master sync has not run yet - and until 2026-10-15 a stale copy kept offering "Send
    // Payment Link" in billing. Opening payment settings is the moment the server's answer is
    // in hand, so the view model has to correct the mirror, not just display it.

    private fun readiness(easebuzzEnabled: Boolean) = EasebuzzPaymentReadiness(
        paymentLinkReady = easebuzzEnabled,
        easebuzzEnabled = easebuzzEnabled,
        subMerchantActive = true,
        subMerchantStatus = "ACTIVE"
    )

    private fun localProfile(easebuzzEnabled: Boolean) = RestaurantProfileEntity(
        id = 1L,
        restaurantId = 7L,
        easebuzzEnabled = easebuzzEnabled
    )

    private fun onboardingStatus() = EasebuzzOnboardingStatusResponse(
        status = "ACTIVE",
        hasSubMerchant = true,
        isActive = true
    )

    @Test
    fun `loadPaymentReadiness mirrors a server-enabled switch onto a stale local profile`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(onboardingStatus())
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.success(readiness(true))
        every { restaurantRepository.getProfileFlow() } returns flowOf(localProfile(easebuzzEnabled = false))
        val saved = slot<RestaurantProfileEntity>()
        coEvery { restaurantRepository.saveProfileLocally(capture(saved)) } returns Unit

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        assertTrue("the stale local row should have been corrected", saved.isCaptured)
        assertTrue(saved.captured.easebuzzEnabled)
    }

    @Test
    fun `loadPaymentReadiness turns a server-disabled switch off on the device`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(onboardingStatus())
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.success(readiness(false))
        every { restaurantRepository.getProfileFlow() } returns flowOf(localProfile(easebuzzEnabled = true))
        val saved = slot<RestaurantProfileEntity>()
        coEvery { restaurantRepository.saveProfileLocally(capture(saved)) } returns Unit

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        assertTrue("the stale local row should have been corrected", saved.isCaptured)
        assertFalse(saved.captured.easebuzzEnabled)
    }

    @Test
    fun `loadPaymentReadiness writes nothing when the local switch already matches the server`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(onboardingStatus())
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.success(readiness(true))
        every { restaurantRepository.getProfileFlow() } returns flowOf(localProfile(easebuzzEnabled = true))

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        coVerify(exactly = 0) { restaurantRepository.saveProfileLocally(any()) }
    }

    @Test
    fun `loadPaymentReadiness writes nothing when the server cannot be reached`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(onboardingStatus())
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.failure(RuntimeException("Network error"))
        every { restaurantRepository.getProfileFlow() } returns flowOf(localProfile(easebuzzEnabled = true))

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        // An unanswered question must not switch online payments off on the till.
        coVerify(exactly = 0) { restaurantRepository.saveProfileLocally(any()) }
        assertTrue(
            viewModel.paymentReadiness.value is
                com.khanabook.lite.pos.feature.payments.viewmodel.PaymentReadinessUiState.Unavailable
        )
    }

    @Test
    fun `loadPaymentReadiness writes nothing when the device has no profile row yet`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(onboardingStatus())
        coEvery { onboardingRepository.getPaymentReadiness() } returns Result.success(readiness(true))
        every { restaurantRepository.getProfileFlow() } returns flowOf(null)

        val viewModel = createViewModel()
        viewModel.loadPaymentReadiness()
        advanceUntilIdle()

        coVerify(exactly = 0) { restaurantRepository.saveProfileLocally(any()) }
    }

    @Test
    fun `business type starts empty so the form cannot default to sole proprietorship`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(onboardingStatus())

        val viewModel = createViewModel()
        advanceUntilIdle()

        // A defaulted SOLE_PROPRIETORSHIP made every entity submit with the proprietorship
        // nature code and proof rules; the owner must choose the type explicitly.
        assertEquals("", viewModel.businessType)
        // Same for the outlet format: Easebuzz locks the MCC at creation, so defaulting it would
        // permanently file every bar and QSR as dine-in (5812).
        assertEquals("", viewModel.restaurantFormat)
    }

    @Test
    fun `picking a document offers an actionable portal link instead of claiming success`() = runTest(testScheduler) {
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(onboardingStatus())
        coEvery { onboardingRepository.requestKycPortalLink(any(), any()) } returns Result.success(
            mapOf(
                "status" to "portal_required",
                "message" to "Upload this document on the Easebuzz portal to finish verification.",
                "kycUrl" to "https://kyc.easebuzz.in/session/abc"
            )
        )

        val events = mutableListOf<OnboardingEvent>()
        val viewModel = createViewModel()
        val collected = launch(testDispatcher) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.requestKycPortalLink("id_proof")
        advanceUntilIdle()
        collected.cancel()

        val toasts = events.filterIsInstance<OnboardingEvent.Toast>()
        assertEquals(1, toasts.size)
        val toast = toasts.single()
        assertTrue("the fallback must be surfaced as an error", toast.isError)
        assertTrue(
            "the message must name the portal",
            toast.message.contains("portal", ignoreCase = true)
        )
        // The message is only actionable if it carries the link: this screen has no portal
        // button of its own, so without these the merchant is stranded.
        assertEquals("Open portal", toast.actionLabel)
        assertEquals("https://kyc.easebuzz.in/session/abc", toast.actionUrl)
        assertFalse(
            "picking a document must never be reported as a completed upload",
            toasts.any { it.message == "Document uploaded successfully" }
        )
        // Only the document type crosses the wire: Easebuzz accepts no bytes, so an upload would
        // be a multi-megabyte transfer that the server is required to throw away.
        coVerify(exactly = 1) { onboardingRepository.requestKycPortalLink("id_proof", null) }
    }

    @Test
    fun `a failed link request still hands over the cached portal link`() = runTest(testScheduler) {
        // An app that ships before the server does hits a 404 here. The merchant must not be
        // stranded on the screen that exists to send them to the portal.
        coEvery { onboardingRepository.getOnboardingStatus() } returns Result.success(
            onboardingStatus().copy(kycUrl = "https://kyc.easebuzz.in/session/cached")
        )
        coEvery { onboardingRepository.requestKycPortalLink(any(), any()) } returns
            Result.failure(IllegalStateException("HTTP 404"))

        val events = mutableListOf<OnboardingEvent>()
        val viewModel = createViewModel()
        val collected = launch(testDispatcher) { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.requestKycPortalLink("bank_proof")
        advanceUntilIdle()
        collected.cancel()

        val toast = events.filterIsInstance<OnboardingEvent.Toast>().last()
        assertEquals("Open portal", toast.actionLabel)
        assertEquals("https://kyc.easebuzz.in/session/cached", toast.actionUrl)
    }
}
