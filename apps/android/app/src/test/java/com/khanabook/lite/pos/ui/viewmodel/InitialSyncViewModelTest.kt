package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.core.network.TerminalReclaimRequest
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.menu.data.MenuRepository
import com.khanabook.lite.pos.feature.sync.domain.SyncManager
import com.khanabook.lite.pos.feature.sync.viewmodel.InitialSyncState
import com.khanabook.lite.pos.feature.sync.viewmodel.InitialSyncViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class InitialSyncViewModelTest {

    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)
    private val api: KhanaBookApi = mockk(relaxed = true)
    private val menuRepository: MenuRepository = mockk(relaxed = true)
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher

    private val contactOwnerMessage =
        "This device is already set up as another counter. Contact your shop owner."

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
        every { android.util.Log.e(any(), any(), any()) } returns 0

        // Initial pull fails with a plain error, so init settles into a state we then overwrite.
        coEvery { syncManager.performMasterPull() } returns
            Result.failure(IllegalStateException("offline in test"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun httpError(code: Int, body: String): HttpException {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }

    private fun createViewModel(): InitialSyncViewModel =
        InitialSyncViewModel(sessionManager, syncManager, api, menuRepository)

    @Test
    fun `reclaim 409 with DEVICE_ALREADY_BOUND shows the shop owner message`() {
        val vm = createViewModel()
        advanceAndStub(
            httpError(409, """{"code":"DEVICE_ALREADY_BOUND","message":"bound"}""")
        )

        vm.reclaimTerminal("A")
        testScheduler.advanceUntilIdle()

        val state = vm.syncState.value
        assertTrue("expected Error but was $state", state is InitialSyncState.Error)
        assertEquals(contactOwnerMessage, (state as InitialSyncState.Error).message)
    }

    @Test
    fun `reclaim 409 with any other conflict code shows the same shop owner message`() {
        val vm = createViewModel()
        advanceAndStub(httpError(409, """{"code":"SOME_OTHER_CONFLICT"}"""))

        vm.reclaimTerminal("A")
        testScheduler.advanceUntilIdle()

        val state = vm.syncState.value
        assertTrue("expected Error but was $state", state is InitialSyncState.Error)
        assertEquals(contactOwnerMessage, (state as InitialSyncState.Error).message)
    }

    @Test
    fun `reclaim non-409 HTTP error keeps the generic fallback, not the owner message`() {
        val vm = createViewModel()
        advanceAndStub(httpError(500, """{"message":"boom"}"""))

        vm.reclaimTerminal("A")
        testScheduler.advanceUntilIdle()

        val state = vm.syncState.value
        assertTrue("expected Error but was $state", state is InitialSyncState.Error)
        assertFalse(
            "500 must not be reported as a counter conflict",
            (state as InitialSyncState.Error).message == contactOwnerMessage
        )
    }

    @Test
    fun `reclaim non-HTTP failure keeps the generic fallback`() {
        val vm = createViewModel()
        advanceAndStub(java.io.IOException("network down"))

        vm.reclaimTerminal("A")
        testScheduler.advanceUntilIdle()

        val state = vm.syncState.value
        assertTrue("expected Error but was $state", state is InitialSyncState.Error)
        assertFalse((state as InitialSyncState.Error).message == contactOwnerMessage)
    }

    /** Lets the init pull settle, then makes the reclaim call throw [error]. */
    private fun advanceAndStub(error: Throwable) {
        testScheduler.advanceUntilIdle()
        coEvery { api.reclaimTerminal(any<TerminalReclaimRequest>()) } throws error
    }
}
