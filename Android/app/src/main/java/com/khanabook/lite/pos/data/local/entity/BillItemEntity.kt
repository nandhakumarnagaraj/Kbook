package com.khanabook.lite.pos.data.local.entity


import androidx.room.*
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["bill_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MenuItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["menu_item_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ItemVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["bill_id"]),
        Index(value = ["menu_item_id"]),
        Index(value = ["variant_id"])
    ]
)
data class BillItemEntity(
    @SerializedName("localId") @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @SerializedName("billId")
    @ColumnInfo(name = "bill_id")
    val billId: Long,
    @SerializedName("menuItemId")
    @ColumnInfo(name = "menu_item_id")
    val menuItemId: Long?,
    @SerializedName("itemName")
    @ColumnInfo(name = "item_name")
    val itemName: String, 
    @SerializedName("variantId")
    @ColumnInfo(name = "variant_id")
    val variantId: Long? = null,
    @SerializedName("variantName")
    @ColumnInfo(name = "variant_name")
    val variantName: String? = null, 
    @SerializedName("price")
    val price: String, 
    @SerializedName("quantity")
    val quantity: Int,
    @SerializedName("itemTotal")
    @ColumnInfo(name = "item_total")
    val itemTotal: String, 
    @SerializedName("specialInstruction")
    @ColumnInfo(name = "special_instruction")
    val specialInstruction: String? = null,

    @SerializedName("restaurantId")
    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @SerializedName("deviceId")
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @SerializedName("isSynced")
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @SerializedName("updatedAt")
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis()
,
    @SerializedName("isDeleted")
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverBillId") @ColumnInfo(name = "server_bill_id") val serverBillId: Long? = null,
    @SerializedName("serverMenuItemId") @ColumnInfo(name = "server_menu_item_id") val serverMenuItemId: Long? = null,
    @SerializedName("serverVariantId") @ColumnInfo(name = "server_variant_id") val serverVariantId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)


