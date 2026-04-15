package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import com.khanabook.lite.pos.domain.util.SYNC_CONFLICT_MESSAGE
import com.khanabook.lite.pos.domain.util.SyncConflictException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncManagerTest {

    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val api: KhanaBookApi = mockk(relaxed = true)
    private val masterSyncProcessor: MasterSyncProcessor = mockk(relaxed = true)

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { sessionManager.getDeviceId() } returns "device-1"
        every { sessionManager.getLastSyncTimestamp() } returns 10L
        syncManager = SyncManager(sessionManager, api, masterSyncProcessor)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `performFullSync pulls latest data and returns conflict on push 409`() = runTest {
        coEvery { masterSyncProcessor.pushAll() } throws SyncConflictException()
        coEvery { api.pullMasterSync(10L, "device-1") } returns MasterSyncResponse(serverTimestamp = 42L)

        val result = syncManager.performFullSync()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SyncConflictException)
        assertEquals(SYNC_CONFLICT_MESSAGE, result.exceptionOrNull()?.message)
        coVerify(exactly = 1) { masterSyncProcessor.insertMasterData(match { it.serverTimestamp == 42L }) }
        coVerify(exactly = 1) { sessionManager.saveLastSyncTimestamp(42L) }
    }
}
