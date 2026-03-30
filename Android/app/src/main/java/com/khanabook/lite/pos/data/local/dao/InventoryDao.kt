package com.khanabook.lite.pos.data.local.dao


import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.StockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Insert
    suspend fun insertStockLog(log: StockLogEntity)

    @Query("SELECT * FROM stock_logs WHERE menu_item_id = :itemId ORDER BY created_at DESC")
    fun getLogsForItem(itemId: Long): Flow<List<StockLogEntity>>

    @Query("SELECT * FROM stock_logs ORDER BY created_at DESC LIMIT 100")
    fun getAllLogs(): Flow<List<StockLogEntity>>


    @Query("SELECT * FROM stock_logs WHERE is_synced = 0")
    suspend fun getUnsyncedStockLogs(): List<com.khanabook.lite.pos.data.local.entity.StockLogEntity>

    @Query("UPDATE stock_logs SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markStockLogsAsSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedStockLogs(items: List<com.khanabook.lite.pos.data.local.entity.StockLogEntity>)

    @Query("DELETE FROM stock_logs WHERE is_synced = 1")
    suspend fun deleteAllSyncedStockLogs()
}


