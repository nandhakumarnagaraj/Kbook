package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "item_recipes",
    foreignKeys = [
        ForeignKey(
            entity = RawMaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_material_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["restaurant_id"]),
        Index(value = ["restaurant_id", "menu_item_id"]),
        Index(value = ["raw_material_id"])
    ]
)
data class ItemRecipeEntity(
    @SerializedName("id") @PrimaryKey
    val id: Long, // Server ID is primary key

    @SerializedName("restaurantId")
    @ColumnInfo(name = "restaurant_id", defaultValue = "0")
    val restaurantId: Long = 0,

    @SerializedName("menuItemId")
    @ColumnInfo(name = "menu_item_id")
    val menuItemId: Long,

    @SerializedName("rawMaterialId")
    @ColumnInfo(name = "raw_material_id")
    val rawMaterialId: Long,

    @SerializedName("quantityPerItem")
    @ColumnInfo(name = "quantity_per_item", defaultValue = "'0.0'")
    val quantityPerItem: String = "0.0",

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
