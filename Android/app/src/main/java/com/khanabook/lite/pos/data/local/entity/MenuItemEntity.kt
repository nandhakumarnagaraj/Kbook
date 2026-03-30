package com.khanabook.lite.pos.data.local.entity

import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "menu_items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["is_available"])
    ]
)
data class MenuItemEntity(
    @SerializedName("localId") @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    val name: String,
    @ColumnInfo(name = "base_price")
    val basePrice: String, 
    @ColumnInfo(name = "food_type", defaultValue = "veg")
    val foodType: String = "veg",
    val description: String? = null,
    @ColumnInfo(name = "is_available", defaultValue = "1")
    val isAvailable: Boolean = true,
    @ColumnInfo(name = "current_stock", defaultValue = "'0.0'")
    val currentStock: String = "0.0",
    @ColumnInfo(name = "low_stock_threshold", defaultValue = "'10.0'")
    val lowStockThreshold: String = "10.0",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "barcode", defaultValue = "NULL")
    val barcode: String? = null,

    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverCategoryId") @ColumnInfo(name = "server_category_id") val serverCategoryId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)
