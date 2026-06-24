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
        customerWhatsapp: String? = null
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
        customerWhatsapp = customerWhatsapp
    )

    @Test
    fun getBillsByDateRange_returnsOnlyActiveRestaurant() = runBlocking {
        billDao.insertBill(bill(R1, createdAt = 1_000))
        billDao.insertBill(bill(R1, createdAt = 2_000))
        billDao.insertBill(bill(R2, createdAt = 1_500))

        val r1Bills = billDao.getBillsByDateRange(0, 10_000, R1).first()
        assertEquals(2, r1Bills.size)
        assertTrue(r1Bills.all { it.restaurantId == R1 })

        val r2Bills = billDao.getBillsByDateRange(0, 10_000, R2).first()
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

        val forR1UserA = billDao.getLatestPendingOnlineBill(R1, USER_A)
        assertNotNull(forR1UserA)
        assertEquals(R1, forR1UserA!!.restaurantId)

        // Active restaurant R1 must never resolve R2's pending UPI bill, even for a wrong user.
        val forR1UserB = billDao.getLatestPendingOnlineBill(R1, USER_B)
        assertNull(forR1UserB)
    }

    @Test
    fun getRecentBillsWithCustomers_returnsOnlyActiveRestaurant() = runBlocking {
        billDao.insertBill(bill(R1, createdAt = 1_000, customerWhatsapp = "9000000001"))
        billDao.insertBill(bill(R2, createdAt = 2_000, customerWhatsapp = "9000000002"))

        val recents = billDao.getRecentBillsWithCustomers(R1)
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

        val r1Pending = billDao.getBillsWithPendingKds(R1)
        assertEquals(1, r1Pending.size)
        assertEquals(R1, r1Pending.first().restaurantId)
    }
}
