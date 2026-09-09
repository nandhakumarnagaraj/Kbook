package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "raw_materials",
    indices = [
        Index(value = ["restaurant_id"]),
        Index(value = ["restaurant_id", "name"], unique = true)
    ]
)
data class RawMaterialEntity(
    @SerializedName("id") @PrimaryKey
    val id: Long, // Server ID is primary key for synced master materials

    @SerializedName("restaurantId")
    @ColumnInfo(name = "restaurant_id", defaultValue = "0")
    val restaurantId: Long = 0,

    @SerializedName("name")
    val name: String,

    @SerializedName("unit")
    @ColumnInfo(defaultValue = "'kg'")
    val unit: String = "kg",

    @SerializedName("stockQuantity")
    @ColumnInfo(name = "stock_quantity", defaultValue = "'0.0'")
    val stockQuantity: String = "0.0",

    @SerializedName("lowStockThreshold")
    @ColumnInfo(name = "low_stock_threshold", defaultValue = "'0.0'")
    val lowStockThreshold: String = "0.0",

    @SerializedName("costPerUnit")
    @ColumnInfo(name = "cost_per_unit", defaultValue = "NULL")
    val costPerUnit: String? = null,

    @SerializedName("expiryDate")
    @ColumnInfo(name = "expiry_date", defaultValue = "NULL")
    val expiryDate: Long? = null,

    @SerializedName("isDeleted")
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,

    @SerializedName("createdAt")
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("updatedAt")
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
