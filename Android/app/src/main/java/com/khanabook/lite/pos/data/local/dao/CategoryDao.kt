package com.khanabook.lite.pos.data.local.dao


import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT id, server_id as serverId FROM categories WHERE server_id IS NOT NULL AND restaurant_id = :restaurantId")
    suspend fun getAllCategoryServerIds(restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE is_deleted = 0 AND restaurant_id = :restaurantId ORDER BY sort_order ASC")
    fun getAllCategoriesFlow(restaurantId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllCategoriesOnce(restaurantId: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun getCategoryById(id: Long, restaurantId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND restaurant_id = :restaurantId COLLATE NOCASE LIMIT 1")
    suspend fun getCategoryByName(name: String, restaurantId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE is_active = 1 AND is_deleted = 0 AND restaurant_id = :restaurantId ORDER BY sort_order ASC")
    fun getActiveCategoriesFlow(restaurantId: Long): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET is_active = :isActive WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun toggleActive(id: Long, isActive: Boolean, restaurantId: Long)

    @Query(
        "UPDATE categories SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE id = :id AND restaurant_id = :restaurantId"
    )
    suspend fun markDeleted(id: Long, updatedAt: Long, restaurantId: Long)

    @Update
    suspend fun updateCategory(category: CategoryEntity)


    @Query("SELECT * FROM categories WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedCategories(restaurantId: Long): List<com.khanabook.lite.pos.data.local.entity.CategoryEntity>

    @Query("UPDATE categories SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markCategoriesAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE categories SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedCategories(items: List<com.khanabook.lite.pos.data.local.entity.CategoryEntity>)
}
