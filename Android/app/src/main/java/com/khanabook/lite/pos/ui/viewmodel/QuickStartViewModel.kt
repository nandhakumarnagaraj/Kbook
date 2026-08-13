package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.CategoryEntity
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.repository.CategoryRepository
import com.khanabook.lite.pos.data.repository.MenuRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.ui.screens.QuickMenuItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QuickStartViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val menuRepository: MenuRepository,
    private val restaurantRepository: RestaurantRepository,
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _completionEvent = MutableSharedFlow<Unit>()
    val completionEvent: SharedFlow<Unit> = _completionEvent.asSharedFlow()

    fun completeQuickStart(shopName: String, items: List<QuickMenuItem>) {
        if (shopName.isBlank()) {
            _error.value = "Please enter your shop name"
            return
        }
        if (items.isEmpty() || items.none { it.name.isNotBlank() && it.price.isNotBlank() }) {
            _error.value = "Please add at least one menu item"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                withContext(Dispatchers.IO) {
                    // 1. Save/update restaurant profile with shop name
                    saveShopName(shopName)

                    // 2. Create a default "General" category
                    val categoryId = categoryRepository.insertCategory(
                        CategoryEntity(
                            name = "General",
                            isVeg = true,
                            sortOrder = 0
                        )
                    )

                    // 3. Insert menu items
                    items.filter { it.name.isNotBlank() && it.price.isNotBlank() }
                        .forEach { item ->
                            val price = item.price.toDoubleOrNull() ?: return@forEach
                            if (price <= 0) return@forEach
                            menuRepository.insertItem(
                                MenuItemEntity(
                                    categoryId = categoryId,
                                    name = item.name.trim(),
                                    basePrice = item.price.trim(),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                }

                // 4. Mark quick start as done
                sessionManager.setQuickStartCompleted(true)
                sessionManager.setInitialSyncCompleted(true)

                // 5. Trigger sync in background (non-blocking)
                syncManager.triggerImmediateSync()

                _completionEvent.emit(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Quick start failed", e)
                _error.value = "Setup failed. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun saveShopName(shopName: String) {
        val restaurantId = sessionManager.getRestaurantId()
        val existing = restaurantRepository.getProfile()
        val profile = existing?.copy(
            shopName = shopName,
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        ) ?: RestaurantProfileEntity(
            id = restaurantId,
            shopName = shopName,
            restaurantId = restaurantId,
            deviceId = sessionManager.getDeviceId(),
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        restaurantRepository.saveProfile(profile)
    }

    companion object {
        private const val TAG = "QuickStartViewModel"
    }
}
