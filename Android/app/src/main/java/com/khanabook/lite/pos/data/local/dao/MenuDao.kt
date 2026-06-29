package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT id, server_id as serverId FROM menu_items WHERE server_id IS NOT NULL AND is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllMenuItemServerIds(restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT id, server_id as serverId FROM item_variants WHERE server_id IS NOT NULL AND is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllVariantServerIds(restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT id, server_id as serverId FROM menu_items WHERE id IN (:ids) AND server_id IS NOT NULL AND restaurant_id = :restaurantId")
    suspend fun getMenuItemServerIdsByLocalIds(ids: List<Long>, restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT id, server_id as serverId FROM item_variants WHERE id IN (:ids) AND server_id IS NOT NULL AND restaurant_id = :restaurantId")
    suspend fun getVariantServerIdsByLocalIds(ids: List<Long>, restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MenuItemEntity): Long

    @Update
    suspend fun updateItem(item: MenuItemEntity)

    @Query("SELECT * FROM menu_items WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun getItemById(id: Long, restaurantId: Long): MenuItemEntity?

    @Query("SELECT * FROM menu_items WHERE name = :name AND restaurant_id = :restaurantId AND is_deleted = 0 LIMIT 1")
    suspend fun getItemByName(name: String, restaurantId: Long): MenuItemEntity?

    @Query("SELECT * FROM menu_items WHERE barcode = :barcode AND restaurant_id = :restaurantId AND is_deleted = 0 LIMIT 1")
    suspend fun getItemByBarcode(barcode: String, restaurantId: Long): MenuItemEntity?

    @Query("SELECT * FROM menu_items WHERE is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllMenuItemsOnce(restaurantId: Long): List<MenuItemEntity>

    @Query("SELECT * FROM item_variants WHERE is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllVariantsOnce(restaurantId: Long): List<ItemVariantEntity>

    @Query("SELECT * FROM menu_items WHERE is_deleted = 0 AND restaurant_id = :restaurantId")
    fun getAllItemsFlow(restaurantId: Long): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE category_id = :categoryId AND restaurant_id = :restaurantId AND is_deleted = 0 ORDER BY name ASC")
    fun getItemsByCategoryFlow(categoryId: Long, restaurantId: Long): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE category_id = :categoryId AND restaurant_id = :restaurantId AND is_deleted = 0 ORDER BY name ASC")
    suspend fun getItemsByCategoryOnce(categoryId: Long, restaurantId: Long): List<MenuItemEntity>

    @Query("SELECT * FROM menu_items WHERE is_deleted = 0 AND restaurant_id = :restaurantId AND (name LIKE :query OR category_id IN (SELECT id FROM categories WHERE name LIKE :query AND restaurant_id = :restaurantId AND is_deleted = 0))")
    fun searchItems(query: String, restaurantId: Long): Flow<List<MenuItemEntity>>

    @Query("UPDATE menu_items SET is_available = :isAvailable WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun toggleItemAvailability(id: Long, isAvailable: Boolean, restaurantId: Long)

    @Query("UPDATE menu_items SET current_stock = current_stock + :delta WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun updateStock(id: Long, delta: Double, restaurantId: Long)

    @Query("UPDATE menu_items SET low_stock_threshold = :threshold WHERE id = :id")
    suspend fun updateLowStockThreshold(id: Long, threshold: Double)

    @Query(
        "UPDATE menu_items SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE id = :id AND restaurant_id = :restaurantId"
    )
    suspend fun markItemDeleted(id: Long, updatedAt: Long, restaurantId: Long)

    @Query(
        "UPDATE menu_items SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE category_id = :categoryId AND restaurant_id = :restaurantId"
    )
    suspend fun markItemsDeletedByCategory(categoryId: Long, updatedAt: Long, restaurantId: Long)

    @Query("SELECT id FROM menu_items WHERE category_id = :categoryId AND restaurant_id = :restaurantId")
    suspend fun getItemIdsByCategory(categoryId: Long, restaurantId: Long): List<Long>

    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ItemVariantEntity): Long

    @Update
    suspend fun updateVariant(variant: ItemVariantEntity)

    @Query("SELECT * FROM item_variants WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun getVariantById(id: Long, restaurantId: Long): ItemVariantEntity?

    @Query("UPDATE item_variants SET current_stock = current_stock + :delta WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun updateVariantStock(id: Long, delta: Double, restaurantId: Long)

    @Query("UPDATE item_variants SET low_stock_threshold = :threshold WHERE id = :id")
    suspend fun updateVariantLowStockThreshold(id: Long, threshold: Double)

    @Query(
        "UPDATE item_variants SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE id = :id AND restaurant_id = :restaurantId"
    )
    suspend fun markVariantDeleted(id: Long, updatedAt: Long, restaurantId: Long)

    @Query(
        "UPDATE item_variants SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt WHERE menu_item_id = :itemId AND restaurant_id = :restaurantId"
    )
    suspend fun markVariantsDeletedByItem(itemId: Long, updatedAt: Long, restaurantId: Long)

    @Query("SELECT * FROM item_variants WHERE menu_item_id = :itemId AND restaurant_id = :restaurantId AND is_deleted = 0 ORDER BY sort_order ASC")
    fun getVariantsForItemFlow(itemId: Long, restaurantId: Long): Flow<List<ItemVariantEntity>>

    @Transaction
    @Query("SELECT * FROM menu_items WHERE category_id = :categoryId AND restaurant_id = :restaurantId AND is_deleted = 0 ORDER BY name ASC")
    fun getMenuWithVariantsByCategoryFlow(categoryId: Long, restaurantId: Long): Flow<List<MenuWithVariants>>

    @Transaction
    @Query("SELECT * FROM menu_items WHERE is_deleted = 0 AND restaurant_id = :restaurantId AND (name LIKE :query OR category_id IN (SELECT id FROM categories WHERE name LIKE :query AND restaurant_id = :restaurantId AND is_deleted = 0)) ORDER BY name ASC")
    fun searchMenuWithVariants(query: String, restaurantId: Long): Flow<List<MenuWithVariants>>

    @Query("SELECT * FROM menu_items WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedMenuItems(restaurantId: Long): List<MenuItemEntity>

    @Query("UPDATE menu_items SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markMenuItemsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE menu_items SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateMenuItemServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedMenuItems(items: List<MenuItemEntity>)

    @Query("""
        UPDATE menu_items
        SET is_deleted = 1
        WHERE restaurant_id = :restaurantId
          AND server_id IN (:serverIds)
          AND id NOT IN (:preferredIds)
    """)
    suspend fun hideDuplicateMenuItemsByServerIds(serverIds: List<Long>, preferredIds: List<Long>, restaurantId: Long)

    @Query("SELECT * FROM item_variants WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedItemVariants(restaurantId: Long): List<ItemVariantEntity>

    @Query("UPDATE item_variants SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markItemVariantsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE item_variants SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateVariantServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedItemVariants(items: List<ItemVariantEntity>)

    @Query("""
        UPDATE item_variants
        SET is_deleted = 1
        WHERE restaurant_id = :restaurantId
          AND server_id IN (:serverIds)
          AND id NOT IN (:preferredIds)
    """)
    suspend fun hideDuplicateVariantsByServerIds(serverIds: List<Long>, preferredIds: List<Long>, restaurantId: Long)
}
