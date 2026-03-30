package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT id, server_id as serverId FROM menu_items WHERE server_id IS NOT NULL")
    suspend fun getAllMenuItemServerIds(): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT id, server_id as serverId FROM item_variants WHERE server_id IS NOT NULL")
    suspend fun getAllVariantServerIds(): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MenuItemEntity): Long

    @Update
    suspend fun updateItem(item: MenuItemEntity)

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getItemById(id: Long): MenuItemEntity?

    @Query("SELECT * FROM menu_items WHERE name = :name AND is_deleted = 0 LIMIT 1")
    suspend fun getItemByName(name: String): MenuItemEntity?

    @Query("SELECT * FROM menu_items WHERE barcode = :barcode AND is_deleted = 0 LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): MenuItemEntity?

    @Query("SELECT * FROM menu_items WHERE is_deleted = 0")
    suspend fun getAllMenuItemsOnce(): List<MenuItemEntity>

    @Query("SELECT * FROM item_variants WHERE is_deleted = 0")
    suspend fun getAllVariantsOnce(): List<ItemVariantEntity>

    @Query("SELECT * FROM menu_items WHERE is_deleted = 0")
    fun getAllItemsFlow(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE category_id = :categoryId AND is_deleted = 0 ORDER BY name ASC")
    fun getItemsByCategoryFlow(categoryId: Long): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE category_id = :categoryId AND is_deleted = 0 ORDER BY name ASC")
    suspend fun getItemsByCategoryOnce(categoryId: Long): List<MenuItemEntity>

    @Query("SELECT * FROM menu_items WHERE is_deleted = 0 AND (name LIKE :query OR category_id IN (SELECT id FROM categories WHERE name LIKE :query AND is_deleted = 0))")
    fun searchItems(query: String): Flow<List<MenuItemEntity>>

    @Query("UPDATE menu_items SET is_available = :isAvailable WHERE id = :id")
    suspend fun toggleItemAvailability(id: Long, isAvailable: Boolean)

    @Query("UPDATE menu_items SET current_stock = current_stock + :delta WHERE id = :id")
    suspend fun updateStock(id: Long, delta: Double)

    @Query("UPDATE menu_items SET low_stock_threshold = :threshold WHERE id = :id")
    suspend fun updateLowStockThreshold(id: Long, threshold: Double)

    @Query(
        "UPDATE menu_items SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun markItemDeleted(id: Long, updatedAt: Long)

    @Query(
        "UPDATE menu_items SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE category_id = :categoryId"
    )
    suspend fun markItemsDeletedByCategory(categoryId: Long, updatedAt: Long)

    @Query("SELECT id FROM menu_items WHERE category_id = :categoryId")
    suspend fun getItemIdsByCategory(categoryId: Long): List<Long>

    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ItemVariantEntity): Long

    @Update
    suspend fun updateVariant(variant: ItemVariantEntity)

    @Query("SELECT * FROM item_variants WHERE id = :id")
    suspend fun getVariantById(id: Long): ItemVariantEntity?

    @Query("UPDATE item_variants SET current_stock = current_stock + :delta WHERE id = :id")
    suspend fun updateVariantStock(id: Long, delta: Double)

    @Query("UPDATE item_variants SET low_stock_threshold = :threshold WHERE id = :id")
    suspend fun updateVariantLowStockThreshold(id: Long, threshold: Double)

    @Query(
        "UPDATE item_variants SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun markVariantDeleted(id: Long, updatedAt: Long)

    @Query(
        "UPDATE item_variants SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE menu_item_id = :itemId"
    )
    suspend fun markVariantsDeletedByItem(itemId: Long, updatedAt: Long)

    @Query("SELECT * FROM item_variants WHERE menu_item_id = :itemId AND is_deleted = 0 ORDER BY sort_order ASC")
    fun getVariantsForItemFlow(itemId: Long): Flow<List<ItemVariantEntity>>

    @Transaction
    @Query("SELECT * FROM menu_items WHERE category_id = :categoryId AND is_deleted = 0 ORDER BY name ASC")
    fun getMenuWithVariantsByCategoryFlow(categoryId: Long): Flow<List<MenuWithVariants>>

    @Query("SELECT * FROM menu_items WHERE is_synced = 0")
    suspend fun getUnsyncedMenuItems(): List<MenuItemEntity>

    @Query("UPDATE menu_items SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markMenuItemsAsSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedMenuItems(items: List<MenuItemEntity>)

    @Query("SELECT * FROM item_variants WHERE is_synced = 0")
    suspend fun getUnsyncedItemVariants(): List<ItemVariantEntity>

    @Query("UPDATE item_variants SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markItemVariantsAsSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedItemVariants(items: List<ItemVariantEntity>)
}
