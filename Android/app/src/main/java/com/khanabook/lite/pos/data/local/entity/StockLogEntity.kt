package com.khanabook.lite.pos.data.local.entity

import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "stock_logs",
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
data class StockLogEntity(
    @SerializedName("localId") @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "menu_item_id")
    val menuItemId: Long,
    @ColumnInfo(name = "variant_id")
    val variantId: Long? = null,
    val delta: String,
    val reason: String, 
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverMenuItemId") @ColumnInfo(name = "server_menu_item_id") val serverMenuItemId: Long? = null,
    @SerializedName("serverVariantId") @ColumnInfo(name = "server_variant_id") val serverVariantId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)
