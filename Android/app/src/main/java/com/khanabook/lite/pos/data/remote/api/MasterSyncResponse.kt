package com.khanabook.lite.pos.data.remote.api

import com.khanabook.lite.pos.data.local.entity.*
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class MasterSyncResponse(
    @SerializedName("serverTimestamp") val serverTimestamp: Long = 0L,
    @SerializedName("profiles") val profiles: List<RestaurantProfileEntity> = emptyList(),
    @SerializedName("users") val users: List<UserEntity> = emptyList(),
    @SerializedName("categories") val categories: List<CategoryEntity> = emptyList(),
    @SerializedName("menuItems") val menuItems: List<MenuItemPullDto> = emptyList(),
    @SerializedName("itemVariants") val itemVariants: List<ItemVariantPullDto> = emptyList(),
    @SerializedName("stockLogs") val stockLogs: List<StockLogEntity> = emptyList(),
    @SerializedName("bills") val bills: List<BillEntity> = emptyList(),
    @SerializedName("billItems") val billItems: List<BillItemEntity> = emptyList(),
    @SerializedName("billPayments") val billPayments: List<BillPaymentEntity> = emptyList()
)

data class MenuItemPullDto(
    @SerializedName("localId") val localId: Long? = null,
    @SerializedName("serverId") val serverId: Long? = null,
    @SerializedName("restaurantId") val restaurantId: Long? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("categoryId") val categoryId: Long? = null,
    @SerializedName("serverCategoryId") val serverCategoryId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("basePrice") val basePrice: BigDecimal? = null,
    @SerializedName("foodType") val foodType: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("isAvailable") val isAvailable: Boolean? = null,
    @SerializedName("currentStock") val currentStock: BigDecimal? = null,
    @SerializedName("lowStockThreshold") val lowStockThreshold: BigDecimal? = null,
    @SerializedName("barcode") val barcode: String? = null,
    @SerializedName("createdAt") val createdAt: Long? = null,
    @SerializedName("updatedAt") val updatedAt: Long? = null,
    @SerializedName("isDeleted") val isDeleted: Boolean? = null,
    @SerializedName("serverUpdatedAt") val serverUpdatedAt: Long? = null
)

data class ItemVariantPullDto(
    @SerializedName("localId") val localId: Long? = null,
    @SerializedName("serverId") val serverId: Long? = null,
    @SerializedName("restaurantId") val restaurantId: Long? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("menuItemId") val menuItemId: Long? = null,
    @SerializedName("serverMenuItemId") val serverMenuItemId: Long? = null,
    @SerializedName("variantName") val variantName: String? = null,
    @SerializedName("price") val price: BigDecimal? = null,
    @SerializedName("isAvailable") val isAvailable: Boolean? = null,
    @SerializedName("sortOrder") val sortOrder: Int? = null,
    @SerializedName("currentStock") val currentStock: BigDecimal? = null,
    @SerializedName("lowStockThreshold") val lowStockThreshold: BigDecimal? = null,
    @SerializedName("updatedAt") val updatedAt: Long? = null,
    @SerializedName("isDeleted") val isDeleted: Boolean? = null,
    @SerializedName("serverUpdatedAt") val serverUpdatedAt: Long? = null
)
