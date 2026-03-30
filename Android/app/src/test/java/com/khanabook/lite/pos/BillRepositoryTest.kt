package com.khanabook.lite.pos

import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager
import com.khanabook.lite.pos.domain.model.OrderStatus
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class BillRepositoryTest {

    private lateinit var billDao: BillDao
    private lateinit var restaurantDao: com.khanabook.lite.pos.data.local.dao.RestaurantDao
    private lateinit var inventoryConsumptionManager: InventoryConsumptionManager
    private lateinit var workManager: WorkManager
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
        billRepository = BillRepository(billDao, restaurantDao, inventoryConsumptionManager, workManager)
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
}
