package com.khanabook.lite.pos

import androidx.work.WorkManager
import androidx.work.Operation
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.KotEventDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.entity.KotEventEntity
import com.khanabook.lite.pos.data.local.entity.KotEventType
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.model.OrderStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BillRepositoryTest {

    private lateinit var billDao: BillDao
    private lateinit var restaurantDao: com.khanabook.lite.pos.data.local.dao.RestaurantDao
    private lateinit var inventoryConsumptionManager: InventoryConsumptionManager
    private lateinit var workManager: WorkManager
    private lateinit var kotEventDao: KotEventDao
    private lateinit var sessionManager: SessionManager
    private lateinit var billRepository: BillRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        
        billDao = mockk(relaxed = true)
        restaurantDao = mockk(relaxed = true)
        inventoryConsumptionManager = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } returns mockk<Operation>(relaxed = true)
        kotEventDao = mockk(relaxed = true)
        sessionManager = mock()
        doReturn(12345L).whenever(sessionManager).getActiveUserId()
        doReturn(1L).whenever(sessionManager).getRestaurantId()
        doReturn("A1").whenever(sessionManager).getDeviceId()
        
        billRepository = BillRepository(
            billDao = billDao,
            restaurantDao = restaurantDao,
            inventoryConsumptionManager = inventoryConsumptionManager,
            workManager = workManager,
            kotEventDao = kotEventDao,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `insertFullBill should consume materials if order status is completed`() = runTest {
        val bill = BillEntity(
            id = 1,
            dailyOrderId = 1,
            dailyOrderDisplay = "B1",
            lifetimeOrderId = 1,
            subtotal = "100.00",
            totalAmount = "100.00",
            paymentMode = "cash",
            paymentStatus = "success",
            orderStatus = OrderStatus.COMPLETED.dbValue,
            createdAt = System.currentTimeMillis()
        )
        val items = listOf(BillItemEntity(billId = 1, menuItemId = 1, itemName = "Item 1", quantity = 1, price = "100.00", itemTotal = "100.00"))
        val payments = listOf(BillPaymentEntity(billId = 1, paymentMode = "cash", amount = "100.00"))

        billRepository.insertFullBill(bill, items, payments)

        coVerify { billDao.insertFullBill(bill, items, payments) }
        coVerify { inventoryConsumptionManager.consumeMaterialsForBill(items) }
    }

    @Test
    fun `insertFullBill should NOT consume materials if order status is draft`() = runTest {
        val bill = BillEntity(
            id = 1,
            dailyOrderId = 1,
            dailyOrderDisplay = "B1",
            lifetimeOrderId = 1,
            subtotal = "100.00",
            totalAmount = "100.00",
            paymentMode = "cash",
            paymentStatus = "failed",
            orderStatus = "draft",
            createdAt = System.currentTimeMillis()
        )
        val items = listOf(BillItemEntity(billId = 1, menuItemId = 1, itemName = "Item 1", quantity = 1, price = "100.00", itemTotal = "100.00"))
        val payments = emptyList<BillPaymentEntity>()

        billRepository.insertFullBill(bill, items, payments)

        coVerify { billDao.insertFullBill(bill, items, payments) }
        coVerify(inverse = true) { inventoryConsumptionManager.consumeMaterialsForBill(any()) }
    }

    @Test
    fun `insertFullBill records NEW kot event for local printable bill`() = runTest {
        val bill = kotBill(orderStatus = "draft")
        val item = kotItem(billId = 10L)
        coEvery { billDao.insertFullBill(bill, listOf(item), emptyList()) } returns 10L
        coEvery { billDao.getBillWithItemsById(10L, 1L) } returns BillWithItems(bill.copy(id = 10L), listOf(item.copy(billId = 10L)), emptyList())
        coEvery { kotEventDao.getMaxRevisionForBill("token-1") } returns 0L
        val event = slot<KotEventEntity>()

        billRepository.insertFullBill(bill, listOf(item), emptyList())

        coVerify { kotEventDao.insert(capture(event)) }
        assert(event.captured.eventType == KotEventType.NEW)
        assert(event.captured.publicToken == "token-1")
        assert(event.captured.kotRevision == "1")
        assert(event.captured.originatingDeviceId == "A1")
    }

    @Test
    fun `insertBillItems records ADD kot event`() = runTest {
        val bill = kotBill(id = 10L, orderStatus = "draft")
        val item = kotItem(billId = 10L, quantity = 2)
        coEvery { billDao.getBillById(10L, 1L) } returns bill
        coEvery { kotEventDao.getMaxRevisionForBill("token-1") } returns 1L
        val event = slot<KotEventEntity>()

        billRepository.insertBillItems(listOf(item))

        coVerify { kotEventDao.insert(capture(event)) }
        assert(event.captured.eventType == KotEventType.ADD)
        assert(event.captured.kotRevision == "2")
        assert(event.captured.itemSnapshotJson.contains("\"quantity\":2"))
    }

    @Test
    fun `deleteBillItemById records VOID kot event`() = runTest {
        val bill = kotBill(id = 10L, orderStatus = "draft")
        val item = kotItem(id = 99L, billId = 10L, quantity = 1)
        coEvery { billDao.getBillItemsByIds(listOf(99L), 1L) } returns listOf(item)
        coEvery { billDao.getBillById(10L, 1L) } returns bill
        coEvery { kotEventDao.getMaxRevisionForBill("token-1") } returns 2L
        val event = slot<KotEventEntity>()

        billRepository.deleteBillItemById(99L)

        coVerify { kotEventDao.insert(capture(event)) }
        assert(event.captured.eventType == KotEventType.VOID)
        assert(event.captured.kotRevision == "3")
    }

    @Test
    fun `remote-owned bill does not record kot event`() = runTest {
        val bill = kotBill(id = 10L, deviceId = "A2", orderStatus = "draft")
        coEvery { billDao.getBillById(10L, 1L) } returns bill

        billRepository.insertBillItems(listOf(kotItem(billId = 10L)))

        coVerify(inverse = true) { kotEventDao.insert(any()) }
    }

    private fun kotBill(
        id: Long = 0L,
        deviceId: String = "A1",
        orderStatus: String
    ) = BillEntity(
        id = id,
        dailyOrderId = 1,
        dailyOrderDisplay = "B1",
        lifetimeOrderId = null,
        subtotal = "100.00",
        totalAmount = "100.00",
        paymentMode = "cash",
        paymentStatus = "pending",
        orderStatus = orderStatus,
        createdAt = System.currentTimeMillis(),
        publicToken = "token-1",
        restaurantId = 1L,
        deviceId = deviceId
    )

    private fun kotItem(
        id: Long = 1L,
        billId: Long,
        quantity: Int = 1
    ) = BillItemEntity(
        id = id,
        billId = billId,
        menuItemId = 1,
        itemName = "Item 1",
        quantity = quantity,
        price = "100.00",
        itemTotal = "100.00",
        restaurantId = 1L,
        deviceId = "A1"
    )
}
