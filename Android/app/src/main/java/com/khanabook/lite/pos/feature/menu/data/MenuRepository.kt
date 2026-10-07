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
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getItemById(item.id, restaurantId)
        val computed = changedFields ?: computeChangedFields(item, current)

        // A null/blank mask means "the whole record" to the server's field-mask merge,
        // so a no-op update must never be written. computeChangedFields returns null
        // precisely when nothing changed, and the old code carried that null straight
        // through: it re-normalized the price, marked the row dirty, triggered a push,
        // and persisted changedFields = null - which the server reads as "overwrite
        // every field", so an accidental no-op edit would silently replace every
        // server-side value with this device's possibly-stale copy.
        // An explicit "all" is not blank, so callers that mean "full record" still work.
        if (computed.isNullOrBlank()) return

        // The diff above is taken against the LOCAL row, which already carries this edit the
        // moment it is written - so a second edit to a DIFFERENT field diffs clean for the
        // first and the mask would name only the second field. The server then restores its
        // own value for the first, and that earlier edit silently reverts: a rename followed
        // by a price fix inside one sync debounce hit exactly that. Union in whatever the row
        // still has unacknowledged, so a field this device edited stays owned by this device
        // until a push is acknowledged (markMenuItemsAsSynced clears the mask).
        val fields = mergeChangedFields(
            pending = current?.takeIf { !it.isSynced }?.changedFields,
            computed = computed
        )

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
        if (isWildcard()) return true
        return orEmpty().split(",").any { it.trim().equals(field, ignoreCase = true) }
    }

    /**
     * Union of the mask this edit computed with the mask the row still has unacknowledged.
     *
     * Duplicates are dropped case-insensitively and order is kept. A wildcard on either side
     * means the whole record and wins outright: the row is already the device's to overwrite,
     * so narrowing it here would drop fields instead of preserving them.
     */
    private fun mergeChangedFields(pending: String?, computed: String): String {
        if (pending.isNullOrBlank()) return computed
        if (pending.isWildcard() || computed.isWildcard()) return "all"
        val merged = LinkedHashMap<String, String>()
        for (token in "$pending,$computed".split(",")) {
            val field = token.trim()
            if (field.isNotEmpty()) merged.putIfAbsent(field.lowercase(), field)
        }
        return merged.values.joinToString(",")
    }

    private fun String?.isWildcard(): Boolean {
        val value = this?.trim() ?: return true
        return value.isEmpty() || value.equals("all", true) || value.contains("*")
    }

    /**
     * Updates photo URL and version directly from a successful server upload/delete, without
     * triggering a background push: the upload already reached the server, so re-pushing it
     * would only invite clock-skew conflicts.
     *
     * Deliberately does NOT force is_synced = true. is_synced is a ROW-level flag while this
     * write only acknowledges one column, so forcing it hid every field edit still sitting in
     * changed_fields from getUnsyncedMenuItems, whose push query filters on is_synced = 0 - a
     * rename confirmed in the same dialog as a photo change stopped being pushable the moment
     * the upload returned.
     *
     * The row's sync flags are carried through from one read and written back as a pair. Writing
     * is_synced alone would let a push that acks the row during the upload - seconds long - leave
     * it marked dirty with changed_fields already NULL, a state the server reads as "overwrite
     * every field": the same hazard a stray no-op edit used to cause. Worst case here is one
     * redundant push re-sending the edit the row still owns.
     */
    suspend fun updateItemPhotoMetadata(itemId: Long, imageUrl: String?, imageVersion: Int) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getItemById(itemId, restaurantId)
        menuDao.updateImageMetadataLocally(
            itemId,
            restaurantId,
            imageUrl,
            imageVersion,
            current?.isSynced ?: true,
            current?.changedFields
        )
    }

    /** Detect changed fields by diffing against the persisted row so server
     *  field-level merge only overwrites what was actually edited. */
    private fun computeChangedFields(newItem: MenuItemEntity, current: MenuItemEntity?): String? {
        // A row that is not in the local table yet is a brand-new record: the whole payload
        // is the device's to write.
        if (current == null) return "all"
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
            // Must stay in step with the fields updateItem is willing to skip. A field
            // missing from this diff reads as "unchanged", so a caller flipping it via
            // updateItem would never push it to the server. The variant flow itself is
            // safe - refreshHasVariantsFlag uses its own targeted DAO update.
            if (current.hasVariants != newItem.hasVariants) add("hasVariants")
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
