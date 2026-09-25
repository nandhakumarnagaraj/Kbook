package com.khanabook.lite.pos.feature.menu.data

import androidx.work.WorkManager
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.sync.domain.enqueueMasterSyncOnce
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRepository(
        private val categoryDao: CategoryDao,
        private val menuDao: com.khanabook.lite.pos.feature.menu.data.MenuDao,
        private val sessionManager: SessionManager,
        private val workManager: WorkManager,
        private val permissionManager: com.khanabook.lite.pos.feature.staff.domain.PermissionManager
) {
    suspend fun insertCategory(category: CategoryEntity): Long {
        val restaurantId = sessionManager.getRestaurantId()
        val existing = categoryDao.getCategoryByName(category.name, restaurantId)
        if (existing != null) {
            if (existing.isDeleted) {
                // Undelete
                val enrichedCategory = existing.copy(
                    isDeleted = false,
                    isActive = true,
                    isVeg = category.isVeg,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
                categoryDao.updateCategory(enrichedCategory)
                triggerBackgroundSync()
                return enrichedCategory.id
            } else {
                return existing.id
            }
        }

        val deviceId = sessionManager.getDeviceId()

        val enrichedCategory =
                category.copy(
                        restaurantId = restaurantId,
                        deviceId = deviceId,
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                )
        val id = categoryDao.insertCategory(enrichedCategory)
        triggerBackgroundSync()
        return id
    }

    private fun triggerBackgroundSync() {
        workManager.enqueueMasterSyncOnce()
    }

    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            categoryDao.getAllCategoriesFlow(restaurantId)
        }
    }

    fun getActiveCategoriesFlow(): Flow<List<CategoryEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            categoryDao.getActiveCategoriesFlow(restaurantId)
        }
    }

    suspend fun toggleActive(id: Long, isActive: Boolean) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = categoryDao.getCategoryById(id, restaurantId) ?: return
        updateCategory(current.copy(isActive = isActive))
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        val restaurantId = sessionManager.getRestaurantId()
        val now = System.currentTimeMillis()
        val revision = permissionManager.currentRevision()
        categoryDao.markDeleted(category.id, now, restaurantId)
        val itemIds = menuDao.getItemIdsByCategory(category.id, restaurantId)
        menuDao.markItemsDeletedByCategory(category.id, now, restaurantId, revision)
        itemIds.forEach { itemId ->
            menuDao.markVariantsDeletedByItem(itemId, now, restaurantId)
        }
        triggerBackgroundSync()
    }

    suspend fun updateCategory(category: CategoryEntity) {
        val enrichedCategory =
                category.copy(isSynced = false, updatedAt = System.currentTimeMillis())
        categoryDao.updateCategory(enrichedCategory)
        triggerBackgroundSync()
    }

    suspend fun reorderCategories(ordered: List<CategoryEntity>) {
        val now = System.currentTimeMillis()
        ordered.forEachIndexed { index, category ->
            if (category.sortOrder != index) {
                categoryDao.updateCategory(
                    category.copy(
                        sortOrder = index,
                        isSynced = false,
                        updatedAt = now
                    )
                )
            }
        }
        triggerBackgroundSync()
    }
}
