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
        val now = System.currentTimeMillis()
        menuDao.updateStock(menuItemId, delta)
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
        val current = menuDao.getItemById(menuItemId) ?: return
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
        val current = menuDao.getVariantById(variantId) ?: return
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
            inventoryDao.getLogsForItem(itemId)

    fun getAllLogs(): Flow<List<StockLogEntity>> = inventoryDao.getAllLogs()

    private fun triggerBackgroundSync() {
        val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncWorkRequest =
                OneTimeWorkRequestBuilder<MasterSyncWorker>().setConstraints(constraints).build()
        workManager.enqueueUniqueWork(
            "MasterSyncWorker_OneTime",
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }
}
