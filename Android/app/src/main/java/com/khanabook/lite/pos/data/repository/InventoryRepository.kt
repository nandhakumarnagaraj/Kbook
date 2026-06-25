package com.khanabook.lite.pos.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.InventoryDao
import com.khanabook.lite.pos.data.local.dao.MenuDao
import com.khanabook.lite.pos.data.local.entity.StockLogEntity
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.util.enqueueMasterSyncOnce
import com.khanabook.lite.pos.worker.MasterSyncWorker
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
            isSynced = true,
            updatedAt = System.currentTimeMillis()
        )
        inventoryDao.insertStockLog(enriched)
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
        val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncWorkRequest =
                OneTimeWorkRequestBuilder<MasterSyncWorker>().setConstraints(constraints).build()
        workManager.enqueueMasterSyncOnce(syncWorkRequest)
    }
}
