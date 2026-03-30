package com.khanabook.lite.pos.data.remote.api

import com.khanabook.lite.pos.data.local.entity.*
import com.google.gson.annotations.SerializedName

data class MasterSyncResponse(
    @SerializedName("serverTimestamp") val serverTimestamp: Long = 0L,
    @SerializedName("profiles") val profiles: List<RestaurantProfileEntity> = emptyList(),
    @SerializedName("users") val users: List<UserEntity> = emptyList(),
    @SerializedName("categories") val categories: List<CategoryEntity> = emptyList(),
    @SerializedName("menuItems") val menuItems: List<MenuItemEntity> = emptyList(),
    @SerializedName("itemVariants") val itemVariants: List<ItemVariantEntity> = emptyList(),
    @SerializedName("stockLogs") val stockLogs: List<StockLogEntity> = emptyList(),
    @SerializedName("bills") val bills: List<BillEntity> = emptyList(),
    @SerializedName("billItems") val billItems: List<BillItemEntity> = emptyList(),
    @SerializedName("billPayments") val billPayments: List<BillPaymentEntity> = emptyList()
)
