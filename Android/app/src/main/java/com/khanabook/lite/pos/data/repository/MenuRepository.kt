package com.khanabook.lite.pos.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.MenuDao
import com.khanabook.lite.pos.data.local.entity.ItemVariantEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.relation.MenuWithVariants
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.util.MenuPricingRules
import com.khanabook.lite.pos.domain.util.enqueueMasterSyncOnce
import com.khanabook.lite.pos.worker.MasterSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MenuRepository(
        private val menuDao: MenuDao,
        private val sessionManager: SessionManager,
        private val workManager: WorkManager
) {

    suspend fun insertItem(item: MenuItemEntity): Long {
        val enriched =
                item.copy(
                        restaurantId = sessionManager.getRestaurantId(),
                        deviceId = sessionManager.getDeviceId(),
                        basePrice = MenuPricingRules.normalizePrice(item.basePrice),
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                )
        val id = menuDao.insertItem(enriched)
        triggerBackgroundSync()
        return id
    }

    suspend fun updateItem(item: MenuItemEntity) {
        val enriched = item.copy(
            basePrice = MenuPricingRules.normalizePrice(item.basePrice),
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        menuDao.updateItem(enriched)
        triggerBackgroundSync()
    }

    suspend fun getItemById(id: Long): MenuItemEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemById(id, restaurantId)
    }

    suspend fun getItemOnce(id: Long): MenuItemEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemById(id, restaurantId)
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
        updateItem(current.copy(currentStock = newStock))
    }

    fun getItemsByCategoryFlow(categoryId: Long): Flow<List<MenuItemEntity>> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemsByCategoryFlow(categoryId, restaurantId)
    }

    /** One-shot, non-flow fetch – safe to call from any coroutine context. */
    suspend fun getItemsByCategoryOnce(categoryId: Long): List<MenuItemEntity> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getItemsByCategoryOnce(categoryId, restaurantId)
    }

    fun getAllItemsFlow(): Flow<List<MenuItemEntity>> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getAllItemsFlow(restaurantId)
    }

    fun getMenuWithVariantsByCategoryFlow(categoryId: Long): Flow<List<MenuWithVariants>> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getMenuWithVariantsByCategoryFlow(categoryId, restaurantId)
            .map { list -> list.map { it.copy(variants = it.variants.filterNot(ItemVariantEntity::isDeleted)) } }
    }

    fun searchItems(query: String): Flow<List<MenuItemEntity>> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.searchItems("%$query%", restaurantId)
    }

    fun searchMenuWithVariants(query: String): Flow<List<MenuWithVariants>> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.searchMenuWithVariants("%$query%", restaurantId)
            .map { list -> list.map { it.copy(variants = it.variants.filterNot(ItemVariantEntity::isDeleted)) } }
    }

    suspend fun toggleItemAvailability(id: Long, isAvailable: Boolean) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = menuDao.getItemById(id, restaurantId) ?: return
        updateItem(current.copy(isAvailable = isAvailable))
    }

    suspend fun deleteItem(item: MenuItemEntity) {
        val restaurantId = sessionManager.getRestaurantId()
        val now = System.currentTimeMillis()
        menuDao.markItemDeleted(item.id, now, restaurantId)
        menuDao.markVariantsDeletedByItem(item.id, now, restaurantId)
        triggerBackgroundSync()
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
        triggerBackgroundSync()
        return id
    }

    suspend fun updateVariant(variant: ItemVariantEntity) {
        val enriched = variant.copy(
            price = MenuPricingRules.normalizePrice(variant.price),
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
        updateVariant(current.copy(currentStock = newStock))
    }

    suspend fun deleteVariant(variant: ItemVariantEntity) {
        menuDao.markVariantDeleted(variant.id, System.currentTimeMillis(), sessionManager.getRestaurantId())
        triggerBackgroundSync()
    }

    fun getVariantsForItemFlow(itemId: Long): Flow<List<ItemVariantEntity>> {
        val restaurantId = sessionManager.getRestaurantId()
        return menuDao.getVariantsForItemFlow(itemId, restaurantId)
    }

    private fun triggerBackgroundSync() {
        val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncWorkRequest =
                OneTimeWorkRequestBuilder<MasterSyncWorker>().setConstraints(constraints).build()
        workManager.enqueueMasterSyncOnce(syncWorkRequest)
    }
}
