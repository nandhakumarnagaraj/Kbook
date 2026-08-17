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
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.BillPaymentSyncDto
import com.khanabook.lite.pos.data.remote.dto.BillSyncDto
import com.khanabook.lite.pos.data.remote.dto.PushSyncResponse
import com.khanabook.lite.pos.domain.util.SyncConflictException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
    private lateinit var api: KhanaBookApi
    private lateinit var billDao: BillDao
    private lateinit var restaurantDao: RestaurantDao
    private lateinit var userDao: UserDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var menuDao: MenuDao
    private lateinit var inventoryDao: InventoryDao
    private lateinit var printerProfileDao: PrinterProfileDao
    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        api = mockk(relaxed = true)
        billDao = mockk(relaxed = true)
        restaurantDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        menuDao = mockk(relaxed = true)
        inventoryDao = mockk(relaxed = true)
        printerProfileDao = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        processor = MasterSyncProcessor(
            api = api,
            databaseProvider = mockk<DatabaseProvider>(relaxed = true),
            billDao = billDao,
            restaurantDao = restaurantDao,
            userDao = userDao,
            categoryDao = categoryDao,
            menuDao = menuDao,
            inventoryDao = inventoryDao,
            printerProfileDao = printerProfileDao,
            sessionManager = sessionManager,
            permissionManager = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `repeated batch conflict isolates bad record and preserves valid acknowledgements`() = runTest {
        val attemptedBatches = mutableListOf<List<Long>>()
        val markedSynced = mutableListOf<Long>()

        val result = processor.pushBatches(
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

        assertEquals(listOf(1L, 2L, 4L), result)
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

    @Test
    fun `pulled reconciliation uses client fingerprint instead of server timestamp`() = runTest {
        val local = bill(
            orderStatus = "completed",
            paymentStatus = "success",
            updatedAt = 2_000,
            serverUpdatedAt = 3_000,
            serverId = 500L
        )
        val staleRemote = local.copy(
            orderStatus = "draft",
            paymentStatus = "pending",
            updatedAt = 1_000,
            serverUpdatedAt = 3_000,
            isSynced = true
        )
        coEvery { billDao.getBillByServerId(500L, RESTAURANT_ID) } returns local
        coEvery {
            billDao.markBillAsSyncedIfUnchanged(
                billId = BILL_ID,
                restaurantId = RESTAURANT_ID,
                pushedUpdatedAt = 1_000,
                pushedOrderStatus = "draft",
                pushedPaymentStatus = "pending"
            )
        } returns 0

        val reconciled = processor.reconcilePulledBillsByClientFingerprint(
            listOf(staleRemote),
            RESTAURANT_ID
        )

        assertEquals(0, reconciled)
        coVerify(exactly = 1) {
            billDao.markBillAsSyncedIfUnchanged(
                billId = BILL_ID,
                restaurantId = RESTAURANT_ID,
                pushedUpdatedAt = 1_000,
                pushedOrderStatus = "draft",
                pushedPaymentStatus = "pending"
            )
        }
    }

    @Test
    fun `bill changed during push is pushed again with split payments`() = runTest {
        val draft = bill(
            orderStatus = "draft",
            paymentStatus = "pending",
            paymentMode = "upi",
            updatedAt = 1_000
        )
        val completed = draft.copy(
            orderStatus = "completed",
            paymentStatus = "success",
            paymentMode = "part_cash_upi",
            partAmount1 = "40.00",
            partAmount2 = "60.00",
            updatedAt = 2_000,
            serverId = 500L
        )
        val payments = listOf(
            BillPaymentEntity(
                id = 10L,
                billId = BILL_ID,
                restaurantId = RESTAURANT_ID,
                deviceId = "dev-A",
                paymentMode = "cash",
                amount = "40.00",
                operationId = "bill-token:payment:cash"
            ),
            BillPaymentEntity(
                id = 11L,
                billId = BILL_ID,
                restaurantId = RESTAURANT_ID,
                deviceId = "dev-A",
                paymentMode = "upi",
                amount = "60.00",
                operationId = "bill-token:payment:upi"
            )
        )
        val billPushes = mutableListOf<List<BillSyncDto>>()
        val paymentPushes = mutableListOf<List<BillPaymentSyncDto>>()
        var acknowledgementCalls = 0

        coEvery { billDao.getUnsyncedBills(RESTAURANT_ID) } returns listOf(draft)
        coEvery { userDao.getAllUsersOnce() } returns emptyList()
        coEvery { billDao.getBillById(BILL_ID, RESTAURANT_ID) } returns completed
        coEvery {
            billDao.markBillAsSyncedIfUnchanged(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } answers {
            acknowledgementCalls++
            if (acknowledgementCalls == 1) 0 else 1
        }
        coEvery { api.pushBills(any()) } answers {
            val payload = firstArg<List<BillSyncDto>>()
            billPushes += payload
            PushSyncResponse(
                successfulLocalIds = payload.map { it.localId },
                failedLocalIds = emptyList(),
                localToServerIdMap = mapOf(BILL_ID to 500L)
            )
        }
        coEvery {
            billDao.getUnsyncedBillItemsWithSyncedParent(RESTAURANT_ID)
        } returns emptyList()
        coEvery {
            billDao.getUnsyncedBillPaymentsWithSyncedParent(RESTAURANT_ID)
        } returns payments
        coEvery { api.pushBillPayments(any()) } answers {
            val payload = firstArg<List<BillPaymentSyncDto>>()
            paymentPushes += payload
            PushSyncResponse(
                successfulLocalIds = payload.map { it.localId },
                failedLocalIds = emptyList()
            )
        }

        val result = processor.pushAllForTest(RESTAURANT_ID)

        assertTrue(result)
        assertEquals(2, billPushes.size)
        assertEquals("draft", billPushes[0].single().orderStatus)
        assertEquals("pending", billPushes[0].single().paymentStatus)
        assertEquals("completed", billPushes[1].single().orderStatus)
        assertEquals("success", billPushes[1].single().paymentStatus)
        assertEquals("40.00", billPushes[1].single().partAmount1)
        assertEquals("60.00", billPushes[1].single().partAmount2)
        assertEquals(
            setOf("cash", "upi"),
            paymentPushes.single().map { it.paymentMode }.toSet()
        )
        coVerify(exactly = 1) {
            billDao.markBillAsSyncedIfUnchanged(
                billId = BILL_ID,
                restaurantId = RESTAURANT_ID,
                pushedUpdatedAt = 1_000,
                pushedOrderStatus = "draft",
                pushedPaymentStatus = "pending"
            )
        }
        coVerify(exactly = 1) {
            billDao.markBillAsSyncedIfUnchanged(
                billId = BILL_ID,
                restaurantId = RESTAURANT_ID,
                pushedUpdatedAt = 2_000,
                pushedOrderStatus = "completed",
                pushedPaymentStatus = "success"
            )
        }
    }

    private fun conflict(message: String): HttpException {
        val body = ResponseBody.create(null, """{"error":"$message"}""")
        return HttpException(Response.error<PushSyncResponse>(409, body))
    }

    private fun bill(
        orderStatus: String,
        paymentStatus: String,
        paymentMode: String = "cash",
        updatedAt: Long,
        serverUpdatedAt: Long = 0L,
        serverId: Long? = null
    ) = BillEntity(
        id = BILL_ID,
        restaurantId = RESTAURANT_ID,
        deviceId = "dev-A",
        terminalId = "A",
        createdTerminalId = "A",
        currentOwnerTerminalId = "A",
        dailyOrderId = 1L,
        dailyOrderDisplay = "1",
        lifetimeOrderId = 1L,
        subtotal = "100.00",
        totalAmount = "100.00",
        paymentMode = paymentMode,
        partAmount1 = "0.00",
        partAmount2 = "0.00",
        paymentStatus = paymentStatus,
        orderStatus = orderStatus,
        createdAt = 1_000,
        updatedAt = updatedAt,
        isSynced = false,
        serverId = serverId,
        serverUpdatedAt = serverUpdatedAt,
        publicToken = "bill-token",
        recordOrigin = "local_created",
        recordScope = "terminal_operational"
    )

    private companion object {
        const val RESTAURANT_ID = 101L
        const val BILL_ID = 1L
    }
}
