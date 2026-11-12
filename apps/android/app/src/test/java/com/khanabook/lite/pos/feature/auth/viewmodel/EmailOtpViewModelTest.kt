package com.khanabook.lite.pos.feature.auth.viewmodel

import android.content.Context
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.feature.auth.data.UserRepository
import com.khanabook.lite.pos.feature.auth.domain.AuthManager
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.sync.domain.SyncManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailOtpViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val restaurantRepository: RestaurantRepository = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val authManager: AuthManager = mockk(relaxed = true)
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { userRepository.currentUser } returns MutableStateFlow(null)

        mockkStatic(androidx.work.WorkManager::class)
        val mockWorkManager = mockk<androidx.work.WorkManager>(relaxed = true)
        every { androidx.work.WorkManager.getInstance(any()) } returns mockWorkManager

        viewModel = AuthViewModel(
            context,
            userRepository,
            restaurantRepository,
            syncManager,
            sessionManager,
            authManager,
            PermissionManager(sessionManager, mockk(relaxed = true))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun sendEmailUpdateOtp_success_setsStatusToOtpSent() = runTest {
        val email = "new@khanabook.com"
        coEvery { userRepository.requestEmailUpdateOtp(email) } returns Result.success(Unit)

        viewModel.sendEmailUpdateOtp(email)

        assertEquals(AuthViewModel.OtpVerificationResult.OtpSent, viewModel.emailOtpVerificationStatus.value)
    }

    @Test
    fun confirmEmailUpdate_success_setsStatusToSuccess() = runTest {
        val email = "new@khanabook.com"
        val otp = "123456"
        coEvery { userRepository.confirmEmailUpdate(email, otp) } returns Result.success(Unit)

        viewModel.confirmEmailUpdate(email, otp)

        assertEquals(AuthViewModel.OtpVerificationResult.Success, viewModel.emailOtpVerificationStatus.value)
    }

    @Test
    fun clearEmailOtpStatus_resetsState() = runTest {
        val email = "new@khanabook.com"
        coEvery { userRepository.requestEmailUpdateOtp(email) } returns Result.success(Unit)

        viewModel.sendEmailUpdateOtp(email)
        assertEquals(AuthViewModel.OtpVerificationResult.OtpSent, viewModel.emailOtpVerificationStatus.value)

        viewModel.clearEmailOtpStatus()
        assertEquals(null, viewModel.emailOtpVerificationStatus.value)
        assertTrue(viewModel.emailOtpFieldErrors.value.isEmpty())
    }
}
