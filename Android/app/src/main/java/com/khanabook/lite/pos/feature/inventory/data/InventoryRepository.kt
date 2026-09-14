package com.khanabook.lite.pos.feature.inventory.data

import androidx.work.WorkManager
import com.khanabook.lite.pos.feature.inventory.data.InventoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.inventory.data.StockLogEntity
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.sync.domain.enqueueMasterSyncOnce
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.Flow

class InventoryRepository(
        private val inventoryDao: InventoryDao,
        private val menuDao: MenuDao,
        private val sessionManager: SessionManager,
        private val workManager: WorkManager
) {
    suspend fun adjustStock(menuItemId: Long, delta: Double, reason: String) {
        val restaurantId = sessionManager.getRestaurantId()
        val now = System.currentTimeMillis()
        menuDao.updateStock(menuItemId, delta, restaurantId)
        insertStockLog(
                StockLogEntity(
                        menuItemId = menuItemId,
                        delta = delta.toString(),
                        reason = reason,
                        createdAt = now
                )
        )
    }

    suspend fun insertStockLog(log: StockLogEntity) {
        val enriched = log.copy(
            restaurantId = sessionManager.getRestaurantId(),
            deviceId = sessionManager.getDeviceId(),
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        inventoryDao.insertStockLog(enriched)
        triggerBackgroundSync()
    }

    suspend fun updateThreshold(menuItemId: Long, threshold: Double) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getItemById(menuItemId, restaurantId) ?: return
        menuDao.updateItem(
            current.copy(
                lowStockThreshold = threshold.toString(),
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        triggerBackgroundSync()
    }

    suspend fun updateVariantThreshold(variantId: Long, threshold: Double) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getVariantById(variantId, restaurantId) ?: return
        menuDao.updateVariant(
            current.copy(
                lowStockThreshold = threshold.toString(),
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        triggerBackgroundSync()
    }

    fun getLogsForItem(itemId: Long): Flow<List<StockLogEntity>> =
            inventoryDao.getLogsForItem(itemId, sessionManager.getRestaurantId())

    fun getAllLogs(): Flow<List<StockLogEntity>> {
        val restaurantId = sessionManager.getRestaurantId()
        return inventoryDao.getAllLogs(restaurantId)
    }

    private fun triggerBackgroundSync() {
        workManager.enqueueMasterSyncOnce()
    }
}
