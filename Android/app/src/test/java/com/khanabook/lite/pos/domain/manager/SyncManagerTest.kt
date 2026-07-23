package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import com.khanabook.lite.pos.domain.util.SyncConflictException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.never

// TODO: These tests need Robolectric or a SessionManager interface.
// SyncManager calls sessionManager methods that Mockito struggles with in
// coroutine NonCancellable contexts. The sync flow is more effectively tested
// via the backend TerminalIsolationIntegrationTest and MasterSyncTerminalIsolationTest.
@org.junit.Ignore("Requires Robolectric — SessionManager + coroutine interaction fails under pure Mockito")
class SyncManagerTest {

    private val sessionManager: SessionManager = mock()
    private val api: KhanaBookApi = mockk(relaxed = true)
    private val masterSyncProcessor: MasterSyncProcessor = mockk(relaxed = true)

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        io.mockk.every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        whenever(sessionManager.getDeviceId()).thenReturn("device-1")
        whenever(sessionManager.getLastSyncTimestamp()).thenReturn(10L)
        whenever(sessionManager.getRestaurantId()).thenReturn(0L)
        whenever(sessionManager.getTerminalSeries()).thenReturn("A1")
        whenever(sessionManager.getTerminalId()).thenReturn("1")

        syncManager = SyncManager(sessionManager, api, masterSyncProcessor)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `performFullSync returns success when conflict recovery pull succeeds`() = runTest {
        coEvery { masterSyncProcessor.pushAll() } throws SyncConflictException()
        coEvery { masterSyncProcessor.pushAllAfterConflictRecovery() } returns true
        coEvery { api.pullMasterSync(any(), any(), any(), any(), any(), any()) } returns MasterSyncResponse(serverTimestamp = 42L, hasMore = false)

        val result = syncManager.performFullSync()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { masterSyncProcessor.pushAll() }
        coVerify(exactly = 1) { masterSyncProcessor.pushAllAfterConflictRecovery() }
        coVerify(exactly = 1) { masterSyncProcessor.insertMasterData(match { it.serverTimestamp == 42L }) }
        verify(sessionManager, times(1)).saveLastSyncTimestamp(42L)
    }

    @Test
    fun `performFullSync returns unresolved conflict when recovery pull fails`() = runTest {
        coEvery { masterSyncProcessor.pushAll() } throws SyncConflictException()
        coEvery { api.pullMasterSync(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("pull failed")

        val result = syncManager.performFullSync()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SyncConflictException)
        assertTrue(!(result.exceptionOrNull() as SyncConflictException).recoverySucceeded)
        coVerify(exactly = 1) { masterSyncProcessor.pushAll() }
        verify(sessionManager, never()).saveLastSyncTimestamp(org.mockito.kotlin.any())
    }
}
