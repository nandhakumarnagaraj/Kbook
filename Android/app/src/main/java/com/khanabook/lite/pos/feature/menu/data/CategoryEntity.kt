package com.khanabook.lite.pos.feature.menu.data


import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "categories",
    indices = [Index(value = ["restaurant_id", "name"], unique = true)]
)
data class CategoryEntity(
    @SerializedName("localId") @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "is_veg")
    val isVeg: Boolean, 
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    /**
     * Favourite (pinned) category, delivered on pull and rendered as a star. Server-owned:
     * the owner pins categories in web admin, and the server restores this value on every
     * category push instead of trusting the device (see
     * GenericSyncService.preserveServerOwnedState). Deliberately NOT part of
     * CategorySyncDto — see SyncDtoContractTest.
     */
    @SerializedName("isFavourite")
    @ColumnInfo(name = "is_favourite", defaultValue = "0")
    val isFavourite: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)


