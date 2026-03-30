package com.khanabook.lite.pos.data.local.entity

import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "item_variants",
    foreignKeys = [
        ForeignKey(
            entity = MenuItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["menu_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["menu_item_id"])]
)
data class ItemVariantEntity(
    @SerializedName("localId") @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "menu_item_id")
    val menuItemId: Long,
    @ColumnInfo(name = "variant_name")
    val variantName: String, 
    val price: String,
    @ColumnInfo(name = "is_available", defaultValue = "1")
    val isAvailable: Boolean = true,
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "current_stock", defaultValue = "'0.0'")
    val currentStock: String = "0.0",
    @ColumnInfo(name = "low_stock_threshold", defaultValue = "'10.0'")
    val lowStockThreshold: String = "10.0",

    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverMenuItemId") @ColumnInfo(name = "server_menu_item_id") val serverMenuItemId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)
