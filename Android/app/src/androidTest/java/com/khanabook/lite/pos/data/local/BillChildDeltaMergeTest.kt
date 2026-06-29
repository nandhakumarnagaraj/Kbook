package com.khanabook.lite.pos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillChildDeltaMergeTest {

    private lateinit var db: AppDatabase
    private lateinit var billDao: BillDao

    private val restaurantId = 101L
    private val otherRestaurantId = 202L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        billDao = db.billDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deltaBillItemReplacement_deletesOnlyIncomingServerIds() = runBlocking {
        val billA = billDao.insertBill(bill(restaurantId, lifetimeOrderId = 1L, serverId = 1_001L))
        val billB = billDao.insertBill(bill(restaurantId, lifetimeOrderId = 2L, serverId = 1_002L))

        billDao.insertBillItems(
            listOf(
                item(billA, serverId = 101L, itemName = "Idli"),
                item(billA, serverId = 102L, itemName = "Dosa"),
                item(billA, serverId = 103L, itemName = "Vada"),
                item(billA, serverId = null, itemName = "Unsynced local note", isSynced = false),
                item(billB, serverId = 201L, itemName = "Tea")
            )
        )

        val incoming = item(
            billA,
            serverId = 102L,
            itemName = "Dosa - voided",
            isDeleted = true,
            updatedAt = 2_000L
        )
        billDao.deleteSyncedBillItemsByServerIds(listOf(102L), restaurantId)
        billDao.insertSyncedBillItems(listOf(incoming))

        val billAItems = billDao.getBillWithItemsById(billA, restaurantId)!!.items
        val billBItems = billDao.getBillWithItemsById(billB, restaurantId)!!.items
        val billAByServerId = billAItems.filter { it.serverId != null }.associateBy { it.serverId }

        assertEquals(setOf(101L, 102L, 103L), billAByServerId.keys)
        assertEquals("Idli", billAByServerId[101L]!!.itemName)
        assertEquals("Dosa - voided", billAByServerId[102L]!!.itemName)
        assertTrue(billAByServerId[102L]!!.isDeleted)
        assertEquals("Vada", billAByServerId[103L]!!.itemName)
        assertEquals(1, billAItems.count { !it.isSynced })
        assertEquals(listOf("Tea"), billBItems.map { it.itemName })
    }

    @Test
    fun deltaBillPaymentReplacement_deletesOnlyIncomingServerIdsAndIsIdempotent() = runBlocking {
        val billA = billDao.insertBill(bill(restaurantId, lifetimeOrderId = 1L, serverId = 1_001L))
        val billB = billDao.insertBill(bill(restaurantId, lifetimeOrderId = 2L, serverId = 1_002L))

        billDao.insertBillPayments(
            listOf(
                payment(billA, serverId = 301L, amount = "100.0"),
                payment(billA, serverId = 302L, amount = "50.0"),
                payment(billA, serverId = null, amount = "25.0", isSynced = false),
                payment(billB, serverId = 401L, amount = "75.0")
            )
        )

        val incoming = payment(billA, serverId = 302L, amount = "60.0", paymentMode = "upi", updatedAt = 2_000L)
        repeat(2) {
            billDao.deleteSyncedBillPaymentsByServerIds(listOf(302L), restaurantId)
            billDao.insertSyncedBillPayments(listOf(incoming))
        }

        val billAPayments = billDao.getBillWithItemsById(billA, restaurantId)!!.payments
        val billBPayments = billDao.getBillWithItemsById(billB, restaurantId)!!.payments
        val billAByServerId = billAPayments.filter { it.serverId != null }.associateBy { it.serverId }

        assertEquals(setOf(301L, 302L), billAByServerId.keys)
        assertEquals("100.0", billAByServerId[301L]!!.amount)
        assertEquals("60.0", billAByServerId[302L]!!.amount)
        assertEquals("upi", billAByServerId[302L]!!.paymentMode)
        assertEquals(1, billAPayments.count { !it.isSynced })
        assertEquals(listOf("75.0"), billBPayments.map { it.amount })
    }

    @Test
    fun fullSyncDelete_removesAllSyncedChildrenForRestaurantOnly() = runBlocking {
        val billA = billDao.insertBill(bill(restaurantId, lifetimeOrderId = 1L, serverId = 1_001L))
        val billB = billDao.insertBill(bill(restaurantId, lifetimeOrderId = 2L, serverId = 1_002L))
        val otherRestaurantBill = billDao.insertBill(
            bill(otherRestaurantId, lifetimeOrderId = 3L, serverId = 2_001L)
        )

        billDao.insertBillItems(
            listOf(
                item(billA, serverId = 101L, itemName = "Idli"),
                item(billB, serverId = 201L, itemName = "Tea"),
                item(billB, serverId = null, itemName = "Unsynced local item", isSynced = false),
                item(otherRestaurantBill, serverId = 301L, itemName = "Coffee", restaurant = otherRestaurantId)
            )
        )
        billDao.insertBillPayments(
            listOf(
                payment(billA, serverId = 401L, amount = "100.0"),
                payment(billB, serverId = 501L, amount = "50.0"),
                payment(billB, serverId = null, amount = "25.0", isSynced = false),
                payment(otherRestaurantBill, serverId = 601L, amount = "75.0", restaurant = otherRestaurantId)
            )
        )

        billDao.deleteAllSyncedBillItems(restaurantId)
        billDao.deleteAllSyncedBillPayments(restaurantId)

        val billAWithChildren = billDao.getBillWithItemsById(billA, restaurantId)
        val billBWithChildren = billDao.getBillWithItemsById(billB, restaurantId)
        val otherWithChildren = billDao.getBillWithItemsById(otherRestaurantBill, otherRestaurantId)

        assertNotNull(billAWithChildren)
        assertEquals(0, billAWithChildren!!.items.size)
        assertEquals(0, billAWithChildren.payments.size)
        assertEquals(listOf("Unsynced local item"), billBWithChildren!!.items.map { it.itemName })
        assertEquals(listOf("25.0"), billBWithChildren.payments.map { it.amount })
        assertEquals(listOf("Coffee"), otherWithChildren!!.items.map { it.itemName })
        assertEquals(listOf("75.0"), otherWithChildren.payments.map { it.amount })
    }

    private fun bill(
        restaurant: Long,
        lifetimeOrderId: Long,
        serverId: Long
    ) = BillEntity(
        restaurantId = restaurant,
        deviceId = "device-1",
        dailyOrderId = lifetimeOrderId,
        dailyOrderDisplay = lifetimeOrderId.toString(),
        lifetimeOrderId = lifetimeOrderId,
        subtotal = "100.0",
        totalAmount = "100.0",
        paymentMode = "cash",
        paymentStatus = "paid",
        orderStatus = "completed",
        isSynced = true,
        serverId = serverId,
        createdAt = 1_000L + lifetimeOrderId,
        updatedAt = 1_000L + lifetimeOrderId
    )

    private fun item(
        billId: Long,
        serverId: Long?,
        itemName: String,
        isSynced: Boolean = true,
        isDeleted: Boolean = false,
        updatedAt: Long = 1_000L,
        restaurant: Long = restaurantId
    ) = BillItemEntity(
        billId = billId,
        menuItemId = null,
        itemName = itemName,
        price = "10.0",
        quantity = 1,
        itemTotal = "10.0",
        restaurantId = restaurant,
        deviceId = "device-1",
        isSynced = isSynced,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        serverId = serverId,
        serverBillId = 9_000L + billId,
        serverUpdatedAt = updatedAt
    )

    private fun payment(
        billId: Long,
        serverId: Long?,
        amount: String,
        paymentMode: String = "cash",
        isSynced: Boolean = true,
        updatedAt: Long = 1_000L,
        restaurant: Long = restaurantId
    ) = BillPaymentEntity(
        billId = billId,
        paymentMode = paymentMode,
        amount = amount,
        restaurantId = restaurant,
        deviceId = "device-1",
        isSynced = isSynced,
        updatedAt = updatedAt,
        serverId = serverId,
        serverBillId = 9_000L + billId,
        serverUpdatedAt = updatedAt
    )
}
