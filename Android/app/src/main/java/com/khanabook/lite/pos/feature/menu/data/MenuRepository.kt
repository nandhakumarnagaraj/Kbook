package com.khanabook.lite.pos.feature.menu.data

import androidx.work.WorkManager
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.MenuWithVariants
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.menu.domain.MenuPricingRules
import com.khanabook.lite.pos.feature.sync.domain.enqueueMasterSyncOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class MenuRepository(
        private val menuDao: MenuDao,
        private val sessionManager: SessionManager,
        private val workManager: WorkManager,
        private val permissionManager: com.khanabook.lite.pos.feature.staff.domain.PermissionManager
) {

    suspend fun insertItem(item: MenuItemEntity): Long {
        val enriched =
                item.copy(
                        restaurantId = sessionManager.getRestaurantId(),
                        deviceId = sessionManager.getDeviceId(),
                        basePrice = MenuPricingRules.normalizePrice(item.basePrice),
                        isSynced = false,
                        updatedAt = System.currentTimeMillis(),
                        permissionRevisionAtCreation = permissionManager.currentRevision(),
                        changedFields = "all"
                )
        val id = menuDao.insertItem(enriched)
        triggerBackgroundSync()
        return id
    }

    suspend fun updateItem(item: MenuItemEntity, changedFields: String? = null) {
        val fields = changedFields ?: computeChangedFields(item)
        val enriched = item.copy(
            basePrice = if (fields.changesField("basePrice")) {
                MenuPricingRules.normalizePrice(item.basePrice)
            } else {
                item.basePrice
            },
            isSynced = false,
            updatedAt = System.currentTimeMillis(),
            permissionRevisionAtCreation = permissionManager.currentRevision(),
            changedFields = fields
        )
        menuDao.updateItem(enriched)
        triggerBackgroundSync()
    }

    /**
     * Mirrors the server's field-mask semantics in GenericSyncService.applyChangedFieldsMerge:
     * a null/blank/"all"/wildcard mask means a full record, otherwise the mask is a
     * comma-separated list of the fields the device actually changed.
     */
    private fun String?.changesField(field: String): Boolean {
        if (this == null) return true
        val trimmed = trim()
        if (trimmed.isEmpty() || trimmed.equals("all", true) || trimmed.contains("*")) return true
        return split(",").any { it.trim().equals(field, ignoreCase = true) }
    }

    /**
     * Updates photo URL and version directly from a successful server upload/delete.
     * Marks the record as is_synced = true without triggering a background push,
     * preventing clock-skew sync conflicts.
     */
    suspend fun updateItemPhotoMetadata(itemId: Long, imageUrl: String?, imageVersion: Int) {
        val restaurantId = sessionManager.getRestaurantId()
        menuDao.updateImageMetadataLocally(itemId, restaurantId, imageUrl, imageVersion)
    }

    /** Detect changed fields by diffing against the persisted row so server
     *  field-level merge only overwrites what was actually edited. */
    private suspend fun computeChangedFields(newItem: MenuItemEntity): String? {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getItemById(newItem.id, restaurantId) ?: return "all"
        return buildList {
            if (current.name != newItem.name) add("name")
            if (current.description != newItem.description) add("description")
            if (current.basePrice != newItem.basePrice) add("basePrice")
            if (current.categoryId != newItem.categoryId) add("categoryId")
            if (current.isAvailable != newItem.isAvailable) add("isAvailable")
            if (current.currentStock != newItem.currentStock) add("currentStock")
            if (current.lowStockThreshold != newItem.lowStockThreshold) add("lowStockThreshold")
            if (current.foodType != newItem.foodType) add("foodType")
            if (current.barcode != newItem.barcode) add("barcode")
            if (current.imageUrl != newItem.imageUrl) add("imageUrl")
            if (current.imageVersion != newItem.imageVersion) add("imageVersion")
        }.joinToString(",").ifEmpty {
            null
        }
    }

    suspend fun getItemById(id: Long): MenuItemEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemById(id, restaurantId)
    }

    suspend fun getItemOnce(id: Long): MenuItemEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemById(id, restaurantId)
    }

    suspend fun getVariantById(id: Long): ItemVariantEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getVariantById(id, restaurantId)
    }

    suspend fun getItemByName(name: String): MenuItemEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemByName(name, restaurantId)
    }

    suspend fun getMenuItemByCode(code: String): MenuItemEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemByBarcode(code, restaurantId)
    }

    suspend fun getAllMenuItemsOnce(): List<MenuItemEntity> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getAllMenuItemsOnce(restaurantId)
    }

    suspend fun getAllVariantsOnce(): List<ItemVariantEntity> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getAllVariantsOnce(restaurantId)
    }

    suspend fun updateStock(id: Long, delta: String) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getItemById(id, restaurantId) ?: return
        val newStock = try {
            java.math.BigDecimal(current.currentStock.ifBlank { "0.0" })
                .add(java.math.BigDecimal(delta.ifBlank { "0.0" }))
                .toString()
        } catch (e: NumberFormatException) {
            android.util.Log.w("MenuRepository", "Invalid stock value: '${current.currentStock}' or delta: '$delta'", e)
            "0.0"
        }
        updateItem(current.copy(currentStock = newStock), changedFields = "currentStock")
    }

    fun getItemsByCategoryFlow(categoryId: Long): Flow<List<MenuItemEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            menuDao.getItemsByCategoryFlow(categoryId, restaurantId)
        }
    }

    /** One-shot, non-flow fetch – safe to call from any coroutine context. */
    suspend fun getItemsByCategoryOnce(categoryId: Long): List<MenuItemEntity> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemsByCategoryOnce(categoryId, restaurantId)
    }

    fun getAllItemsFlow(): Flow<List<MenuItemEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            menuDao.getAllItemsFlow(restaurantId)
        }
    }

    fun getMenuWithVariantsByCategoryFlow(categoryId: Long): Flow<List<MenuWithVariants>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            menuDao.getMenuWithVariantsByCategoryFlow(categoryId, restaurantId)
        }.map { list ->
            list.map { it.copy(variants = it.variants.filterNot(ItemVariantEntity::isDeleted)) }
        }
    }

    fun searchItems(query: String): Flow<List<MenuItemEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            menuDao.searchItems("%$query%", restaurantId)
        }
    }

    fun searchMenuWithVariants(query: String): Flow<List<MenuWithVariants>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            menuDao.searchMenuWithVariants("%$query%", restaurantId)
        }.map { list ->
            list.map { it.copy(variants = it.variants.filterNot(ItemVariantEntity::isDeleted)) }
        }
    }

    suspend fun toggleItemAvailability(id: Long, isAvailable: Boolean) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getItemById(id, restaurantId) ?: return
        updateItem(current.copy(isAvailable = isAvailable), changedFields = "isAvailable")
    }

    suspend fun deleteItem(item: MenuItemEntity) {
        val restaurantId = sessionManager.getRestaurantId()
        val now = System.currentTimeMillis()
        menuDao.markItemDeleted(item.id, now, restaurantId, permissionManager.currentRevision())
        menuDao.markVariantsDeletedByItem(item.id, now, restaurantId)
        triggerBackgroundSync()
    }

    /**
     * Recompute [MenuItemEntity.hasVariants] from the variant table and persist it only
     * when it actually changed.
     *
     * This is the single choke point that keeps the flag honest. Deriving it here rather
     * than trusting callers means adding, renaming, or removing a variant can never leave
     * the parent claiming a mode it does not have — which is the class of bug that
     * produced the menu save crash documented in
     * docs/reviews/KHANABOOK_MENU_PRICE_SYNC_DATA_LOSS_2026-09-27.md.
     */
    private suspend fun refreshHasVariantsFlag(itemId: Long) {
        val restaurantId = sessionManager.getRestaurantId()
        val shouldHaveVariants = menuDao.countLiveVariants(itemId, restaurantId) > 0
        val current = menuDao.getItemById(itemId, restaurantId) ?: return
        if (current.hasVariants == shouldHaveVariants) return
        menuDao.updateItemHasVariantsFlag(
            itemId = itemId,
            hasVariants = shouldHaveVariants,
            updatedAt = System.currentTimeMillis(),
            restaurantId = restaurantId
        )
    }

    suspend fun insertVariant(variant: ItemVariantEntity): Long {
        val enriched =
                variant.copy(
                        restaurantId = sessionManager.getRestaurantId(),
                        deviceId = sessionManager.getDeviceId(),
                        price = MenuPricingRules.normalizePrice(variant.price),
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                )
        val id = menuDao.insertVariant(enriched)
        refreshHasVariantsFlag(variant.menuItemId)
        triggerBackgroundSync()
        return id
    }
    suspend fun updateVariant(variant: ItemVariantEntity, normalizePrice: Boolean = true) {
        val enriched =
                variant.copy(
                        price = if (normalizePrice) {
                            MenuPricingRules.normalizePrice(variant.price)
                        } else {
                            variant.price
                        },
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                )
        menuDao.updateVariant(enriched)
        triggerBackgroundSync()
    }

    suspend fun updateVariantStock(id: Long, delta: String) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getVariantById(id, restaurantId) ?: return
        val newStock = try {
            java.math.BigDecimal(current.currentStock.ifBlank { "0.0" })
                .add(java.math.BigDecimal(delta.ifBlank { "0.0" }))
                .toString()
        } catch (e: NumberFormatException) {
            android.util.Log.w("MenuRepository", "Invalid variant stock: '${current.currentStock}' or delta: '$delta'", e)
            "0.0"
        }
        updateVariant(current.copy(currentStock = newStock), normalizePrice = false)
    }

    suspend fun deleteVariant(variant: ItemVariantEntity) {
        menuDao.markVariantDeleted(variant.id, System.currentTimeMillis(), sessionManager.getRestaurantId())
        refreshHasVariantsFlag(variant.menuItemId)
        triggerBackgroundSync()
    }

    fun getVariantsForItemFlow(itemId: Long): Flow<List<ItemVariantEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            menuDao.getVariantsForItemFlow(itemId, restaurantId)
        }
    }

    private fun triggerBackgroundSync() {
        workManager.enqueueMasterSyncOnce()
    }
}
