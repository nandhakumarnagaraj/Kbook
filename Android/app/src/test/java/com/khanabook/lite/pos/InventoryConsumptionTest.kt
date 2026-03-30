package com.khanabook.lite.pos

import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.StockLogEntity
import com.khanabook.lite.pos.data.repository.InventoryRepository
import com.khanabook.lite.pos.data.repository.MenuRepository
import com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class InventoryConsumptionTest {

    private lateinit var menuRepository: MenuRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var inventoryConsumptionManager: InventoryConsumptionManager

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        
        menuRepository = mockk(relaxed = true)
        inventoryRepository = mockk(relaxed = true)
        inventoryConsumptionManager = InventoryConsumptionManager(menuRepository, inventoryRepository)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `consumeMaterialsForBill should deduct stock and log base item sale`() = runTest {
        val billItems = listOf(
            BillItemEntity(
                id = 1,
                billId = 42,
                menuItemId = 101,
                itemName = "Biryani",
                quantity = 3,
                price = "200.00",
                itemTotal = "600.00"
            )
        )

        inventoryConsumptionManager.consumeMaterialsForBill(billItems)

        coVerify { menuRepository.updateStock(101, "-3") }
        coVerify { inventoryRepository.insertStockLog(match { 
            it.menuItemId == 101L && it.variantId == null && it.delta == "-3" && it.reason == "Sale (Bill #42)"
        }) }
    }

    @Test
    fun `consumeMaterialsForBill should deduct variant stock and log variant sale`() = runTest {
        val billItems = listOf(
            BillItemEntity(
                id = 1,
                billId = 7,
                menuItemId = 101,
                itemName = "Tea",
                variantId = 501,
                variantName = "Large",
                quantity = 2,
                price = "30.00",
                itemTotal = "60.00"
            )
        )

        inventoryConsumptionManager.consumeMaterialsForBill(billItems)

        coVerify { menuRepository.updateVariantStock(501, "-2") }
        coVerify { inventoryRepository.insertStockLog(match {
            it.menuItemId == 101L && it.variantId == 501L && it.delta == "-2" && it.reason == "Sale (Bill #7)"
        }) }
    }
}
