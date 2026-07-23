package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.DatabaseProvider
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.CategoryDao
import com.khanabook.lite.pos.data.local.dao.InventoryDao
import com.khanabook.lite.pos.data.local.dao.MenuDao
import com.khanabook.lite.pos.data.local.dao.PrinterProfileDao
import com.khanabook.lite.pos.data.local.dao.RestaurantDao
import com.khanabook.lite.pos.data.local.dao.UserDao
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.PushSyncResponse
import com.khanabook.lite.pos.domain.util.SyncConflictException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class MasterSyncProcessorConflictIsolationTest {

    private lateinit var processor: MasterSyncProcessor

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = mockk<DatabaseProvider>(relaxed = true),
            billDao = mockk<BillDao>(relaxed = true),
            restaurantDao = mockk<RestaurantDao>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            menuDao = mockk<MenuDao>(relaxed = true),
            inventoryDao = mockk<InventoryDao>(relaxed = true),
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = mockk<SessionManager>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `repeated batch conflict isolates bad record and preserves valid acknowledgements`() = runTest {
        val attemptedBatches = mutableListOf<List<Long>>()
        val markedSynced = mutableListOf<Long>()

        val result = runCatching {
            processor.pushBatches(
                label = "bills",
                records = listOf(1L, 2L, 3L, 4L),
                localId = { it },
                transform = { it },
                push = { batch ->
                    attemptedBatches += batch
                    if (3L in batch) throw conflict("invoice identity already exists")
                    PushSyncResponse(
                        successfulLocalIds = batch,
                        failedLocalIds = emptyList()
                    )
                },
                markSynced = { markedSynced += it },
                isolateHttpConflicts = true
            )
        }

        assertTrue(result.exceptionOrNull() is SyncConflictException)
        val exception = result.exceptionOrNull() as SyncConflictException
        assertEquals(listOf(3L), exception.failedLocalIds)
        assertEquals("invoice identity already exists", exception.failedReasons[3L])
        assertEquals("bills", exception.syncEntityLabel)
        assertEquals(listOf(1L, 2L, 4L), markedSynced)
        assertEquals(
            listOf(
                listOf(1L, 2L, 3L, 4L),
                listOf(1L, 2L),
                listOf(3L, 4L),
                listOf(3L),
                listOf(4L)
            ),
            attemptedBatches
        )
    }

    @Test
    fun `first batch conflict requests recovery without isolation calls`() = runTest {
        var pushCalls = 0

        val result = runCatching {
            processor.pushBatches(
                label = "bill payments",
                records = listOf(10L, 11L),
                localId = { it },
                transform = { it },
                push = {
                    pushCalls++
                    throw conflict("payment conflict")
                },
                markSynced = {}
            )
        }

        assertEquals(1, pushCalls)
        assertTrue(result.exceptionOrNull() is SyncConflictException)
        val exception = result.exceptionOrNull() as SyncConflictException
        assertEquals(listOf(10L, 11L), exception.failedLocalIds)
        assertEquals("bill payments", exception.syncEntityLabel)
    }

    private fun conflict(message: String): HttpException {
        val body = ResponseBody.create(null, """{"error":"$message"}""")
        return HttpException(Response.error<PushSyncResponse>(409, body))
    }
}
