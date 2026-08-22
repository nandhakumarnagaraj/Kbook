package com.khanabook.lite.pos.data.remote.api

import com.google.gson.annotations.SerializedName

// ── Raw materials ─────────────────────────────────────────────────────────────

data class RawMaterialDto(
    @SerializedName("id") val id: Long,
    @SerializedName("restaurantId") val restaurantId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("unit") val unit: String,
    @SerializedName("stockQuantity") val stockQuantity: Double,
    @SerializedName("lowStockThreshold") val lowStockThreshold: Double,
    @SerializedName("costPerUnit") val costPerUnit: Double?,
    @SerializedName("isDeleted") val isDeleted: Boolean,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long
)

data class CreateMaterialBody(
    @SerializedName("name") val name: String,
    @SerializedName("unit") val unit: String? = null,
    @SerializedName("stockQuantity") val stockQuantity: Double? = null,
    @SerializedName("lowStockThreshold") val lowStockThreshold: Double? = null,
    @SerializedName("costPerUnit") val costPerUnit: Double? = null
)

data class UpdateMaterialBody(
    @SerializedName("name") val name: String? = null,
    @SerializedName("unit") val unit: String? = null,
    @SerializedName("stockQuantity") val stockQuantity: Double? = null,
    @SerializedName("lowStockThreshold") val lowStockThreshold: Double? = null,
    @SerializedName("costPerUnit") val costPerUnit: Double? = null
)

// ── Recipes ───────────────────────────────────────────────────────────────────

data class ItemRecipeDto(
    @SerializedName("id") val id: Long,
    @SerializedName("restaurantId") val restaurantId: Long,
    @SerializedName("menuItemId") val menuItemId: Long,
    @SerializedName("rawMaterial") val rawMaterial: RecipeMaterialDto,
    @SerializedName("quantityPerItem") val quantityPerItem: Double
)

data class RecipeMaterialDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("unit") val unit: String
)

data class CreateRecipeBody(
    @SerializedName("menuItemId") val menuItemId: Long,
    @SerializedName("rawMaterialId") val rawMaterialId: Long,
    @SerializedName("quantityPerItem") val quantityPerItem: Double
)

// ── Analytics ─────────────────────────────────────────────────────────────────

data class ItemSalesRow(
    @SerializedName("menuItemId") val menuItemId: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("quantitySold") val quantitySold: Long,
    @SerializedName("revenue") val revenue: Double?
)

data class HourlySalesRow(
    @SerializedName("hour") val hour: Int,
    @SerializedName("itemsSold") val itemsSold: Long
)

data class FoodCostRow(
    @SerializedName("menuItemId") val menuItemId: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("quantitySold") val quantitySold: Long,
    @SerializedName("revenue") val revenue: Double?,
    @SerializedName("cost") val cost: Double?,
    @SerializedName("configured") val configured: Boolean?,
    @SerializedName("marginPct") val marginPct: Double?
)
