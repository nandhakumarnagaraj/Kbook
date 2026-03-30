package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.AuthManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val restaurantRepository: RestaurantRepository = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val authManager: AuthManager = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { userRepository.currentUser } returns MutableStateFlow(null)

        viewModel = AuthViewModel(
            userRepository,
            restaurantRepository,
            syncManager,
            sessionManager,
            authManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `login returns Success when remote login succeeds`() = runTest {
        val email = "9150677849"
        val password = "owner123"

        val fakeUser = UserEntity(
            id = 1,
            name = "Owner",
            email = email,
            restaurantId = 1,
            deviceId = "device",
            isActive = true
        )

        coEvery { userRepository.remoteLogin(email, password) } returns Result.success(fakeUser)
        coEvery { sessionManager.saveLastSyncTimestamp(any()) } returns Unit
        coEvery { sessionManager.setInitialSyncCompleted(any()) } returns Unit
        coEvery { syncManager.performMasterPull() } returns Result.success(Unit)

        viewModel.login(email, password)
        advanceUntilIdle()

        val result = viewModel.loginStatus.value
        assertTrue("Expected login to succeed", result is AuthViewModel.LoginResult.Success)

        val successResult = result as AuthViewModel.LoginResult.Success
        assertEquals(fakeUser.email, successResult.user.email)
    }

    @Test
    fun `login returns Error when remote login fails with 401`() = runTest {
        val email = "9150677849"
        val password = "wrongPassword"

        val mockResponse = Response.error<Any>(401, okhttp3.ResponseBody.create(null, ""))
        val httpException = HttpException(mockResponse)
        coEvery { userRepository.remoteLogin(email, password) } returns Result.failure(httpException)

        viewModel.login(email, password)
        advanceUntilIdle()

        val result = viewModel.loginStatus.value
        assertTrue("Expected login to fail with INCORRECT_PASSWORD", result is AuthViewModel.LoginResult.Error)

        val errorResult = result as AuthViewModel.LoginResult.Error
        assertEquals(AuthViewModel.LoginErrorCode.INCORRECT_PASSWORD, errorResult.code)
    }

    @Test
    fun `login returns Error when remote login fails with network error and user not found locally`() = runTest {
        val email = "9150677849"
        val password = "owner123"

        coEvery { userRepository.remoteLogin(email, password) } returns Result.failure(java.io.IOException("Network error"))
        coEvery { userRepository.getUserByEmail(email) } returns null

        viewModel.login(email, password)
        advanceUntilIdle()

        val result = viewModel.loginStatus.value
        assertTrue("Expected login to fail with ACCOUNT_NOT_FOUND", result is AuthViewModel.LoginResult.Error)

        val errorResult = result as AuthViewModel.LoginResult.Error
        assertEquals(AuthViewModel.LoginErrorCode.ACCOUNT_NOT_FOUND, errorResult.code)
    }
}
