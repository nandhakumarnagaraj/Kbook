package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.api.CreateMaterialBody
import com.khanabook.lite.pos.data.remote.api.CreateRecipeBody
import com.khanabook.lite.pos.data.remote.api.FoodCostRow
import com.khanabook.lite.pos.data.remote.api.HourlySalesRow
import com.khanabook.lite.pos.data.remote.api.ItemSalesRow
import com.khanabook.lite.pos.data.remote.api.ItemRecipeDto
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.RawMaterialDto
import com.khanabook.lite.pos.data.remote.api.UpdateMaterialBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class InventoryUiState(
    val isLoading: Boolean = true,
    val materials: List<RawMaterialDto> = emptyList(),
    val recipes: Map<Long, List<ItemRecipeDto>> = emptyMap(), // menuItemId -> lines
    val itemSales: List<ItemSalesRow> = emptyList(),
    val hourlySales: List<HourlySalesRow> = emptyList(),
    val foodCost: List<FoodCostRow> = emptyList(),
    val error: String? = null,
    val actionInFlight: Boolean = false
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val api: KhanaBookApi
) : ViewModel() {

    private val tag = "InventoryVM"

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val materials = api.getRawMaterials()
                val today = LocalDate.now()
                val from = today.minusDays(7).toString()
                val to = today.toString()
                val itemSales = try { api.getItemSales(from, to) } catch (e: Exception) { emptyList() }
                val hourly = try { api.getHourlySales(today.toString()) } catch (e: Exception) { emptyList() }
                val foodCost = try { api.getFoodCost(from, to) } catch (e: Exception) { emptyList() }
                _uiState.value = InventoryUiState(
                    isLoading = false,
                    materials = materials,
                    itemSales = itemSales,
                    hourlySales = hourly,
                    foodCost = foodCost
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to load inventory", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load inventory. Check your connection."
                )
            }
        }
    }

    fun createMaterial(name: String, unit: String?, stock: Double?, threshold: Double?, cost: Double?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlight = true)
            try {
                api.createRawMaterial(CreateMaterialBody(name.trim(), unit, stock, threshold, cost))
                _uiState.value = _uiState.value.copy(actionInFlight = false)
                refresh()
            } catch (e: Exception) {
                Log.e(tag, "createMaterial failed", e)
                fail("Could not save material.")
            }
        }
    }

    fun updateStock(materialId: Long, newStock: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlight = true)
            try {
                api.updateRawMaterial(materialId, UpdateMaterialBody(stockQuantity = newStock))
                _uiState.value = _uiState.value.copy(actionInFlight = false)
                refresh()
            } catch (e: Exception) {
                Log.e(tag, "updateStock failed", e)
                fail("Could not update stock.")
            }
        }
    }

    fun deleteMaterial(materialId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlight = true)
            try {
                api.deleteRawMaterial(materialId)
                _uiState.value = _uiState.value.copy(actionInFlight = false)
                refresh()
            } catch (e: Exception) {
                Log.e(tag, "deleteMaterial failed", e)
                fail("Could not delete material.")
            }
        }
    }

    fun loadRecipes(menuItemId: Long) {
        viewModelScope.launch {
            try {
                val lines = api.getItemRecipes(menuItemId)
                _uiState.value = _uiState.value.copy(
                    recipes = _uiState.value.recipes + (menuItemId to lines)
                )
            } catch (e: Exception) {
                Log.e(tag, "loadRecipes failed", e)
            }
        }
    }

    fun addRecipeLine(menuItemId: Long, rawMaterialId: Long, qtyPerItem: Double) {
        if (qtyPerItem <= 0.0) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlight = true)
            try {
                api.createRecipeLine(CreateRecipeBody(menuItemId, rawMaterialId, qtyPerItem))
                _uiState.value = _uiState.value.copy(actionInFlight = false)
                loadRecipes(menuItemId)
            } catch (e: Exception) {
                Log.e(tag, "addRecipeLine failed", e)
                fail("Could not save recipe line.")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(actionInFlight = false, error = message)
    }
}
