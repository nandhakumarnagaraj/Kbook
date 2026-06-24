package com.khanabook.lite.pos.data.local.dao


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}


