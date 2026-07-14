package com.khanabook.lite.pos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.KitchenPrintQueueDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.KitchenPrintDispatchStatus
import com.khanabook.lite.pos.data.local.entity.KitchenPrintQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the bill READ path is isolated by restaurantId when two restaurants'
 * rows coexist in one Room DB (the shared-device scenario). See the home dashboard,
 * reports, drafts, online-payment matching, and KDS read paths.
 */
@RunWith(AndroidJUnit4::class)
class BillDaoIsolationTest {

    private lateinit var db: AppDatabase
    private lateinit var billDao: BillDao
    private lateinit var kdsDao: KitchenPrintQueueDao

    private val R1 = 101L
    private val R2 = 202L
    private val USER_A = 11L
    private val USER_B = 22L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        billDao = db.billDao()
        kdsDao = db.kitchenPrintQueueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bill(
        restaurantId: Long,
        createdAt: Long,
        orderStatus: String = "completed",
        paymentStatus: String = "paid",
        paymentMode: String = "cash",
        ownerUserId: Long? = null,
        customerWhatsapp: String? = null,
        createdTerminalId: String? = "A",
        currentOwnerTerminalId: String? = "A",
        deviceId: String = "dev-A",
        recordOrigin: String = "local_created",
        recordScope: String = "terminal_operational"
    ) = BillEntity(
        restaurantId = restaurantId,
        dailyOrderId = 1L,
        dailyOrderDisplay = "1",
        lifetimeOrderId = createdAt, // unique-ish per row
        subtotal = "100.0",
        totalAmount = "100.0",
        paymentMode = paymentMode,
        paymentStatus = paymentStatus,
        orderStatus = orderStatus,
        createdAt = createdAt,
        ownerUserId = ownerUserId,
        customerWhatsapp = customerWhatsapp,
        createdTerminalId = createdTerminalId,
        currentOwnerTerminalId = currentOwnerTerminalId,
        deviceId = deviceId,
        recordOrigin = recordOrigin,
        recordScope = recordScope
    )

    @Test
    fun getBillsByDateRange_returnsOnlyActiveRestaurant() = runBlocking {
        billDao.insertBill(bill(R1, createdAt = 1_000))
        billDao.insertBill(bill(R1, createdAt = 2_000))
        billDao.insertBill(bill(R2, createdAt = 1_500))

        val r1Bills = billDao.getBillsByDateRange(0, 10_000, R1, "A").first()
        assertEquals(2, r1Bills.size)
        assertTrue(r1Bills.all { it.restaurantId == R1 })

        val r2Bills = billDao.getBillsByDateRange(0, 10_000, R2, "B").first()
        assertEquals(1, r2Bills.size)
        assertTrue(r2Bills.all { it.restaurantId == R2 })
    }

    @Test
    fun getLatestPendingOnlineBill_isScopedByRestaurantAndUser() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_A)
        )
        billDao.insertBill(
            bill(R2, createdAt = 2_000, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_B)
        )

        val forR1UserA = billDao.getLatestPendingOnlineBill(R1, USER_A, "A")
        assertNotNull(forR1UserA)
        assertEquals(R1, forR1UserA!!.restaurantId)

        // Active restaurant R1 must never resolve R2's pending UPI bill, even for a wrong user.
        val forR1UserB = billDao.getLatestPendingOnlineBill(R1, USER_B, "A")
        assertNull(forR1UserB)
    }

    @Test
    fun getRecentBillsWithCustomers_returnsOnlyActiveRestaurant() = runBlocking {
        billDao.insertBill(bill(R1, createdAt = 1_000, customerWhatsapp = "9000000001"))
        billDao.insertBill(bill(R2, createdAt = 2_000, customerWhatsapp = "9000000002"))

        val recents = billDao.getRecentBillsWithCustomers(R1, "A")
        assertEquals(1, recents.size)
        assertEquals("9000000001", recents.first().customerWhatsapp)
    }

    @Test
    fun kds_pendingCountAndBills_areScopedByRestaurant() = runBlocking {
        val b1 = billDao.insertBill(bill(R1, createdAt = 1_000))
        val b2 = billDao.insertBill(bill(R2, createdAt = 2_000))

        kdsDao.upsert(
            KitchenPrintQueueEntity(
                billId = b1, restaurantId = R1, printerMac = "AA:BB",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING
            )
        )
        kdsDao.upsert(
            KitchenPrintQueueEntity(
                billId = b2, restaurantId = R2, printerMac = "CC:DD",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING
            )
        )

        assertEquals(1, kdsDao.getPendingCountFlow(R1).first())
        assertEquals(1, kdsDao.getPendingCountFlow(R2).first())

        val r1Pending = billDao.getBillsWithPendingKds(R1, "A")
        assertEquals(1, r1Pending.size)
        assertEquals(R1, r1Pending.first().restaurantId)
    }

    // ── Terminal ownership isolation (record_scope / record_origin) ──────────────
    //
    // A bill created on Terminal A and pulled onto Terminal B during sync must NOT
    // appear in Terminal B's operational lists (active drafts, drafts, pending online,
    // recent orders, KDS). It is read-only restaurant history on B.

    @Test
    fun getActiveDraftBillsFlow_excludesPulledBillFromOtherTerminal() = runBlocking {
        // Local operational draft on Terminal A.
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        // Same draft pulled onto Terminal B from the server (Terminal A is the origin).
        billDao.insertBill(
            bill(R1, createdAt = 1_100, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                deviceId = "dev-A", recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val drafts = billDao.getActiveDraftBillsFlow(R1, "A").first()
        assertEquals(1, drafts.size)
        assertEquals("local_created", drafts.first().recordOrigin)
        assertEquals("terminal_operational", drafts.first().recordScope)
    }

    @Test
    fun getDraftBills_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, orderStatus = "draft", paymentStatus = "pending",
                createdTerminalId = "B", currentOwnerTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val drafts = billDao.getDraftBills(R1, "A").first()
        assertEquals(1, drafts.size)
        assertEquals("A", drafts.first().createdTerminalId)
    }

    @Test
    fun getPendingOnlineBillsFlow_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_A,
                createdTerminalId = "A", currentOwnerTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, orderStatus = "draft", paymentStatus = "pending",
                paymentMode = "upi", ownerUserId = USER_A,
                createdTerminalId = "B", currentOwnerTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val pending = billDao.getPendingOnlineBillsFlow(R1, "A").first()
        assertEquals(1, pending.size)
        assertEquals("A", pending.first().createdTerminalId)
    }

    @Test
    fun getRecentBillsWithCustomers_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, customerWhatsapp = "9000000001",
                createdTerminalId = "A", recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, customerWhatsapp = "9000000002",
                createdTerminalId = "B", recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val recents = billDao.getRecentBillsWithCustomers(R1, "A")
        assertEquals(1, recents.size)
        assertEquals("9000000001", recents.first().customerWhatsapp)
    }

    @Test
    fun getBillsByDateRange_report_excludesPulledBillFromOtherTerminal() = runBlocking {
        billDao.insertBill(
            bill(R1, createdAt = 1_000, createdTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        billDao.insertBill(
            bill(R1, createdAt = 1_100, createdTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )

        val report = billDao.getBillsByDateRange(0, 10_000, R1, "A").first()
        assertEquals(1, report.size)
        assertEquals("A", report.first().createdTerminalId)
    }

    @Test
    fun getBillsWithPendingKds_excludesPulledBillFromOtherTerminal() = runBlocking {
        val local = billDao.insertBill(
            bill(R1, createdAt = 1_000, createdTerminalId = "A",
                recordOrigin = "local_created", recordScope = "terminal_operational")
        )
        val pulled = billDao.insertBill(
            bill(R1, createdAt = 1_100, createdTerminalId = "B",
                recordOrigin = "server_imported", recordScope = "restaurant_history")
        )
        kdsDao.upsert(
            KitchenPrintQueueEntity(billId = local, restaurantId = R1, printerMac = "AA:BB",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING)
        )
        kdsDao.upsert(
            KitchenPrintQueueEntity(billId = pulled, restaurantId = R1, printerMac = "BB:CC",
                dispatchStatus = KitchenPrintDispatchStatus.PENDING)
        )

        val pending = billDao.getBillsWithPendingKds(R1, "A")
        assertEquals(1, pending.size)
        assertEquals("A", pending.first().createdTerminalId)
    }
}
