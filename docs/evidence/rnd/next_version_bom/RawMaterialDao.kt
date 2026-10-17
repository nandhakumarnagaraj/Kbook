package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.RawMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawMaterialDao {

    @Query("SELECT * FROM raw_materials WHERE restaurant_id = :restaurantId AND is_deleted = 0 ORDER BY name ASC")
    fun getAllFlow(restaurantId: Long): Flow<List<RawMaterialEntity>>

    @Query("SELECT * FROM raw_materials WHERE restaurant_id = :restaurantId AND is_deleted = 0 ORDER BY name ASC")
    suspend fun getAll(restaurantId: Long): List<RawMaterialEntity>

    @Query("SELECT * FROM raw_materials WHERE id = :id AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun getById(id: Long, restaurantId: Long): RawMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(material: RawMaterialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(materials: List<RawMaterialEntity>)

    @Query("UPDATE raw_materials SET stock_quantity = CAST((CAST(stock_quantity AS REAL) - :delta) AS TEXT), updated_at = :now WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun decrementStock(id: Long, delta: Double, restaurantId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE raw_materials SET stock_quantity = :newStock, updated_at = :now WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun updateStock(id: Long, newStock: String, restaurantId: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM raw_materials WHERE restaurant_id = :restaurantId")
    suspend fun deleteAllForRestaurant(restaurantId: Long)
}
