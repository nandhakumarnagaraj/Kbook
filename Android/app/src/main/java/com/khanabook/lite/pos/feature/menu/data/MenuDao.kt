package com.khanabook.lite.pos.feature.menu.data

import androidx.room.*
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import com.khanabook.lite.pos.feature.menu.data.MenuWithVariants
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT id, server_id as serverId FROM menu_items WHERE server_id IS NOT NULL AND is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllMenuItemServerIds(restaurantId: Long): List<com.khanabook.lite.pos.feature.sync.domain.ServerIdMapping>

    @Query("SELECT id, image_url AS imageUrl, image_version AS imageVersion, has_variants AS hasVariants FROM menu_items WHERE restaurant_id = :restaurantId")
    suspend fun getAllMenuItemImageInfo(restaurantId: Long): List<com.khanabook.lite.pos.feature.sync.domain.MenuItemImageInfo>

    @Query("SELECT id, server_id as serverId FROM item_variants WHERE server_id IS NOT NULL AND is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllVariantServerIds(restaurantId: Long): List<com.khanabook.lite.pos.feature.sync.domain.ServerIdMapping>

    @Query("SELECT id, server_id as serverId FROM menu_items WHERE id IN (:ids) AND server_id IS NOT NULL AND restaurant_id = :restaurantId")
    suspend fun getMenuItemServerIdsByLocalIds(ids: List<Long>, restaurantId: Long): List<com.khanabook.lite.pos.feature.sync.domain.ServerIdMapping>

    @Query("SELECT id, server_id as serverId FROM item_variants WHERE id IN (:ids) AND server_id IS NOT NULL AND restaurant_id = :restaurantId")
    suspend fun getVariantServerIdsByLocalIds(ids: List<Long>, restaurantId: Long): List<com.khanabook.lite.pos.feature.sync.domain.ServerIdMapping>

    @Insert(onConflict = OnConflictStrategy.ABORT)
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

    // Single-column writes below must APPEND to changed_fields, never replace it.
    // Replacing dropped every other field this device had already edited but not yet pushed,
    // so the server's field-mask merge restored its own value for those fields and the edit
    // silently reverted - a rename followed by an availability toggle reproduced it. They
    // also mark the row dirty (is_synced = 0), which these writes used to forget:
    // getUnsyncedMenuItems filters on is_synced = 0, so an unflagged row was never pushed at
    // all and the change looked saved locally while the server kept its own value.
    //
    // The append is done in SQL so it is atomic against a concurrent sync ack
    // (markMenuItemsAsSynced sets is_synced = 1, changed_fields = NULL). A read-modify-write
    // in Kotlin would let the ack land in between and re-introduce a lost mask. A wildcard or
    // "all" on the row means the whole record is already this device's to write, so it wins
    // outright; an already-present field is not duplicated because the server lower-cases the
    // mask into a set anyway, but keeping it tidy makes the stored value readable.
    @Query(
        """UPDATE menu_items SET is_available = :isAvailable, is_synced = 0, updated_at = :updatedAt,
        changed_fields = CASE
            WHEN changed_fields IS NULL OR trim(changed_fields) = '' THEN 'isAvailable'
            WHEN lower(trim(changed_fields)) = 'all' OR changed_fields LIKE '%*%' THEN changed_fields
            WHEN instr(',' || replace(lower(changed_fields), ' ', '') || ',', ',isavailable,') > 0 THEN changed_fields
            ELSE changed_fields || ',isAvailable'
        END
        WHERE id = :id AND restaurant_id = :restaurantId"""
    )
    suspend fun toggleItemAvailability(id: Long, isAvailable: Boolean, restaurantId: Long, updatedAt: Long)

    @Query(
        """UPDATE menu_items SET low_stock_threshold = :threshold, is_synced = 0, updated_at = :updatedAt,
        changed_fields = CASE
            WHEN changed_fields IS NULL OR trim(changed_fields) = '' THEN 'lowStockThreshold'
            WHEN lower(trim(changed_fields)) = 'all' OR changed_fields LIKE '%*%' THEN changed_fields
            WHEN instr(',' || replace(lower(changed_fields), ' ', '') || ',', ',lowstockthreshold,') > 0 THEN changed_fields
            ELSE changed_fields || ',lowStockThreshold'
        END
        WHERE id = :id AND restaurant_id = :restaurantId"""
    )
    suspend fun updateLowStockThreshold(id: Long, threshold: Double, restaurantId: Long, updatedAt: Long)

    // is_synced/changed_fields are passed in rather than forced to 1: the caller acknowledges
    // the PHOTO, and forcing the row acked here would hide any field edit still pending in
    // changed_fields from getUnsyncedMenuItems, which filters on is_synced = 0. Both flags are
    // written together, from one caller read, so the row can never be left dirty with a NULL
    // mask - which the server reads as "overwrite every field".
    @Query("UPDATE menu_items SET image_url = :imageUrl, image_version = :imageVersion, is_synced = :isSynced, changed_fields = :changedFields WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun updateImageMetadataLocally(
        id: Long,
        restaurantId: Long,
        imageUrl: String?,
        imageVersion: Int,
        isSynced: Boolean,
        changedFields: String?
    )

    @Query(
        """UPDATE menu_items SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt,
        permission_revision_at_creation = :revision,
        changed_fields = CASE
            WHEN changed_fields IS NULL OR trim(changed_fields) = '' THEN 'isDeleted'
            WHEN lower(trim(changed_fields)) = 'all' OR changed_fields LIKE '%*%' THEN changed_fields
            WHEN instr(',' || replace(lower(changed_fields), ' ', '') || ',', ',isdeleted,') > 0 THEN changed_fields
            ELSE changed_fields || ',isDeleted'
        END
        WHERE id = :id AND restaurant_id = :restaurantId"""
    )
    suspend fun markItemDeleted(id: Long, updatedAt: Long, restaurantId: Long, revision: Long? = null)

    @Query(
        """UPDATE menu_items SET is_deleted = 1, is_synced = 0, updated_at = :updatedAt,
        permission_revision_at_creation = :revision,
        changed_fields = CASE
            WHEN changed_fields IS NULL OR trim(changed_fields) = '' THEN 'isDeleted'
            WHEN lower(trim(changed_fields)) = 'all' OR changed_fields LIKE '%*%' THEN changed_fields
            WHEN instr(',' || replace(lower(changed_fields), ' ', '') || ',', ',isdeleted,') > 0 THEN changed_fields
            ELSE changed_fields || ',isDeleted'
        END
        WHERE category_id = :categoryId AND restaurant_id = :restaurantId"""
    )
    suspend fun markItemsDeletedByCategory(categoryId: Long, updatedAt: Long, restaurantId: Long, revision: Long? = null)

    @Query("SELECT id FROM menu_items WHERE category_id = :categoryId AND restaurant_id = :restaurantId")
    suspend fun getItemIdsByCategory(categoryId: Long, restaurantId: Long): List<Long>

    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVariant(variant: ItemVariantEntity): Long

    @Update
    suspend fun updateVariant(variant: ItemVariantEntity)

    @Query("SELECT * FROM item_variants WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun getVariantById(id: Long, restaurantId: Long): ItemVariantEntity?

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

    @Query("SELECT COUNT(*) FROM item_variants WHERE menu_item_id = :itemId AND restaurant_id = :restaurantId AND is_deleted = 0")
    suspend fun countLiveVariants(itemId: Long, restaurantId: Long): Int

    /**
     * Narrow, targeted write of the derived variant-mode flag.
     *
     * Deliberately not a whole-row [updateItem]: the flag is recomputed from the variant
     * table, so writing it must not roll back an unrelated field the user just edited.
     */
    @Query(
        """UPDATE menu_items SET has_variants = :hasVariants, is_synced = 0, updated_at = :updatedAt,
        changed_fields = CASE
            WHEN changed_fields IS NULL OR trim(changed_fields) = '' THEN 'hasVariants'
            WHEN lower(trim(changed_fields)) = 'all' OR changed_fields LIKE '%*%' THEN changed_fields
            WHEN instr(',' || replace(lower(changed_fields), ' ', '') || ',', ',hasvariants,') > 0 THEN changed_fields
            ELSE changed_fields || ',hasVariants'
        END
        WHERE id = :itemId AND restaurant_id = :restaurantId"""
    )
    suspend fun updateItemHasVariantsFlag(
        itemId: Long,
        hasVariants: Boolean,
        updatedAt: Long,
        restaurantId: Long
    )

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

    @Query("UPDATE menu_items SET is_synced = 1, changed_fields = NULL WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markMenuItemsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE menu_items SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateMenuItemServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncedMenuItems(items: List<MenuItemEntity>)

    @Query(
        """
        SELECT * FROM menu_items
        WHERE restaurant_id = :restaurantId
          AND server_id IS NULL
          AND category_id = :categoryId
          AND lower(trim(name)) = lower(trim(:name))
          LIMIT 1
        """
    )
    suspend fun findUnsyncedItemByName(
        restaurantId: Long,
        categoryId: Long,
        name: String
    ): MenuItemEntity?

    @Transaction
    suspend fun upsertSyncedMenuItems(items: List<MenuItemEntity>) {
        for (item in items) {
            val existing = item.serverId?.let { findItemByServerId(it, item.restaurantId) }
            if (existing != null) {
                // The pulled row is server-authoritative EXCEPT over a row still holding unpushed
                // edits - overwriting that reverted the edit and acked it at the same time, so it
                // vanished from both the device and the server. See MenuPullMergePolicy.
                MenuPullMergePolicy.resolveMenuItem(item, existing)?.let { updateItem(it) }
            } else if (item.serverId != null) {
                // No local row carries this serverId. Before inserting a shadow
                // copy, ADOPT an unsynced local row with the same identity
                // (category + normalized name) — a pending edit/add whose
                // serverId ack was lost (logout/reinstall churn). Linking it
                // completes the handshake instead of duplicating.
                val adoptable = findUnsyncedItemByName(item.restaurantId, item.categoryId, item.name)
                if (adoptable != null) {
                    updateMenuItemServerIdByLocalId(adoptable.id, item.serverId!!, item.restaurantId)
                    // The serverId link above is what completes the handshake; the adoption must
                    // not also discard whatever the row still has pending.
                    MenuPullMergePolicy.resolveMenuItem(item, adoptable)?.let { updateItem(it) }
                } else {
                    val existingById = getItemById(item.id, item.restaurantId)
                    if (existingById != null) {
                        insertItem(item.copy(id = 0L))
                    } else {
                        insertItem(item)
                    }
                }
            } else {
                insertItem(item)
            }
        }
    }

    @Query("SELECT * FROM menu_items WHERE server_id = :serverId AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun findItemByServerId(serverId: Long, restaurantId: Long): MenuItemEntity?

    @Query("""
        UPDATE menu_items
        SET is_deleted = 1, is_synced = 1
        WHERE restaurant_id = :restaurantId
          AND server_id IN (:serverIds)
          AND id NOT IN (:preferredIds)
          AND server_id IN (
              SELECT server_id
              FROM menu_items
              WHERE restaurant_id = :restaurantId
                AND server_id IN (:serverIds)
                AND is_deleted = 0
              GROUP BY server_id
              HAVING COUNT(*) > 1
          )
    """)
    suspend fun hideDuplicateMenuItemsByServerIds(serverIds: List<Long>, preferredIds: List<Long>, restaurantId: Long)

    @Query("SELECT * FROM item_variants WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedItemVariants(restaurantId: Long): List<ItemVariantEntity>

    @Query("UPDATE item_variants SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markItemVariantsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE item_variants SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateVariantServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncedItemVariants(items: List<ItemVariantEntity>)

    @Transaction
    suspend fun upsertSyncedItemVariants(items: List<ItemVariantEntity>) {
        for (variant in items) {
            // Match by serverId first; if the pulled row carries a local id that already
            // exists locally (serverId missing or remapped), match by primary key too —
            // insertVariant uses ABORT and crashed the conflict-recovery pull with
            // UNIQUE constraint failed: item_variants.id (code 1555).
            val existing = variant.serverId?.let { findVariantByServerId(it, variant.restaurantId) }
                ?: getVariantById(variant.id, variant.restaurantId)
            if (existing != null) {
                MenuPullMergePolicy.resolveVariant(variant, existing)?.let { updateVariant(it) }
            } else {
                insertVariant(variant)
            }
        }
    }

    @Query("SELECT * FROM item_variants WHERE server_id = :serverId AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun findVariantByServerId(serverId: Long, restaurantId: Long): ItemVariantEntity?

    @Query("""
        UPDATE item_variants
        SET is_deleted = 1, is_synced = 1
        WHERE restaurant_id = :restaurantId
          AND server_id IN (:serverIds)
          AND id NOT IN (:preferredIds)
          AND server_id IN (
              SELECT server_id
              FROM item_variants
              WHERE restaurant_id = :restaurantId
                AND server_id IN (:serverIds)
                AND is_deleted = 0
              GROUP BY server_id
              HAVING COUNT(*) > 1
          )
    """)
    suspend fun hideDuplicateVariantsByServerIds(serverIds: List<Long>, preferredIds: List<Long>, restaurantId: Long)
}
