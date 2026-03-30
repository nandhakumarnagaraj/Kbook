package com.khanabook.lite.pos.data.local.dao


import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT id, server_id as serverId FROM categories WHERE server_id IS NOT NULL")
    suspend fun getAllCategoryServerIds(): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE is_deleted = 0 ORDER BY sort_order ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE is_deleted = 0")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE is_active = 1 AND is_deleted = 0 ORDER BY sort_order ASC")
    fun getActiveCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET is_active = :isActive WHERE id = :id")
    suspend fun toggleActive(id: Long, isActive: Boolean)

    @Query(
        "UPDATE categories SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun markDeleted(id: Long, updatedAt: Long)

    @Update
    suspend fun updateCategory(category: CategoryEntity)


    @Query("SELECT * FROM categories WHERE is_synced = 0")
    suspend fun getUnsyncedCategories(): List<com.khanabook.lite.pos.data.local.entity.CategoryEntity>

    @Query("UPDATE categories SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markCategoriesAsSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedCategories(items: List<com.khanabook.lite.pos.data.local.entity.CategoryEntity>)
}
