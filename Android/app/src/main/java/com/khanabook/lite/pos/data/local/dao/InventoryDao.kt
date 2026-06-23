package com.khanabook.lite.pos.data.local.dao


import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.StockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Insert
    suspend fun insertStockLog(log: StockLogEntity)

    @Query("SELECT * FROM stock_logs WHERE menu_item_id = :itemId AND restaurant_id = :restaurantId ORDER BY created_at DESC")
    fun getLogsForItem(itemId: Long, restaurantId: Long): Flow<List<StockLogEntity>>

    @Query("SELECT * FROM stock_logs WHERE restaurant_id = :restaurantId ORDER BY created_at DESC LIMIT 100")
    fun getAllLogs(restaurantId: Long): Flow<List<StockLogEntity>>


    @Query("SELECT * FROM stock_logs WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedStockLogs(restaurantId: Long): List<com.khanabook.lite.pos.data.local.entity.StockLogEntity>

    @Query("UPDATE stock_logs SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markStockLogsAsSynced(ids: List<Long>, restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedStockLogs(items: List<com.khanabook.lite.pos.data.local.entity.StockLogEntity>)

    @Query("DELETE FROM stock_logs WHERE is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteAllSyncedStockLogs(restaurantId: Long)
}


