package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Contract test (issues.txt fix plan item 5): /auth/check-user is an
 * ANTI-ENUMERATION endpoint — the server intentionally returns true for every
 * valid 10-digit number. It must NEVER be used to gate signup with an
 * "number already exists" error.
 *
 * Regression guard: if anyone wires checkUserExists() back to the network
 * call and flags existence from its response, old-build bug v21 returns and
 * every genuinely-new signup is falsely blocked on-device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckUserContractTest {

    private lateinit var viewModel: AuthViewModel
    private val context: android.content.Context = mockk(relaxed = true)
    private val userRepository: com.khanabook.lite.pos.feature.auth.data.UserRepository = mockk(relaxed = true)
    private val restaurantRepository: com.khanabook.lite.pos.feature.auth.data.RestaurantRepository = mockk(relaxed = true)
    private val syncManager: com.khanabook.lite.pos.feature.sync.domain.SyncManager = mockk(relaxed = true)
    private val sessionManager: com.khanabook.lite.pos.feature.auth.domain.SessionManager = mockk(relaxed = true)
    private val authManager: com.khanabook.lite.pos.feature.auth.domain.AuthManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.d(any(), any()) } returns 0
        io.mockk.every { android.util.Log.i(any(), any()) } returns 0
        io.mockk.every { android.util.Log.e(any(), any()) } returns 0
        io.mockk.every { android.util.Log.e(any(), any(), any()) } returns 0
        io.mockk.every { userRepository.currentUser } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        io.mockk.mockkStatic(androidx.work.WorkManager::class)
        io.mockk.every { androidx.work.WorkManager.getInstance(any()) } returns mockk(relaxed = true)

        viewModel = AuthViewModel(
            context,
            userRepository,
            restaurantRepository,
            syncManager,
            sessionManager,
            authManager,
            com.khanabook.lite.pos.feature.staff.domain.PermissionManager(sessionManager, mockk(relaxed = true))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    @Test
    fun `checkUserExists never flags an existing-account error`() {
        viewModel.checkUserExists("9450341518")

        assertNull(
            "check-user must never produce a userExistsError — it is an anti-enumeration stub",
            viewModel.userExistsError.value
        )
        assertFalse(
            "check-user must never set the user-checking state",
            viewModel.isUserChecking.value
        )
    }

    @Test
    fun `checkUserExists makes no network call`() {
        viewModel.checkUserExists("9450341518")

        // The repository's remote calls must never be touched from the
        // check-user path — the authoritative existence check is /auth/signup.
        io.mockk.coVerify(exactly = 0) { userRepository.remoteLogin(any(), any()) }
    }

    @Test
    fun `clearUserCheck resets state without errors`() {
        viewModel.clearUserCheck()

        assertNull(viewModel.userExistsError.value)
        assertFalse(viewModel.isUserChecking.value)
    }
}
