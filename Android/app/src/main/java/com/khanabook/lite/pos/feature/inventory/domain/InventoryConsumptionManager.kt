package com.khanabook.lite.pos.feature.inventory.domain

import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.inventory.data.StockLogEntity
import com.khanabook.lite.pos.feature.inventory.data.InventoryRepository
import com.khanabook.lite.pos.feature.menu.data.MenuRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryConsumptionManager @Inject constructor(
    private val menuRepository: MenuRepository,
    private val inventoryRepository: InventoryRepository
) {
    
    suspend fun consumeMaterialsForBill(items: List<BillItemEntity>) {
        for (item in items) {
            val delta = "-${item.quantity}"
            val variantId: Long? = item.variantId
            val menuItemId: Long? = item.menuItemId

            if (variantId != null) {
                
                menuRepository.updateVariantStock(variantId, delta)

                
                inventoryRepository.insertStockLog(
                    StockLogEntity(
                        menuItemId = menuItemId ?: 0,
                        variantId = variantId,
                        delta = delta,
                        reason = "Sale (Bill #${item.billId})",
                        createdAt = System.currentTimeMillis()
                    )
                )
            } else if (menuItemId != null) {
                
                menuRepository.updateStock(menuItemId, delta)

                
                inventoryRepository.insertStockLog(
                    StockLogEntity(
                        menuItemId = menuItemId,
                        delta = delta,
                        reason = "Sale (Bill #${item.billId})",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
