package com.khanabook.lite.pos.feature.menu.data

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
    // LEGACY COLUMNS - kept only because the Room schema still declares them (removing them
    // needs a table-rebuild migration for menu_items). Stock belongs to web-admin + the
    // server: these two are off the sync wire in both directions and the master pull now
    // leaves them at their schema defaults instead of storing the server's value.
    @ColumnInfo(name = "current_stock", defaultValue = "'0.0'")
    val currentStock: String = "0.0",
    @ColumnInfo(name = "low_stock_threshold", defaultValue = "'10.0'")
    val lowStockThreshold: String = "10.0",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "barcode", defaultValue = "NULL")
    val barcode: String? = null,
    @SerializedName("imageUrl") @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    @SerializedName("imageVersion") @ColumnInfo(name = "image_version", defaultValue = "0") val imageVersion: Int = 0,
    /**
     * True when this row only groups [ItemVariantEntity] rows and [basePrice] is a
     * derived display value ("from Rs.150"), not a payable price.
     *
     * Explicit on purpose. Pricing mode used to be inferred from a blank or zero
     * [basePrice], which forced every caller to guess and is what crashed the menu save
     * dialog. See docs/design/MENU_ITEM_MODEL_INDIA_FIT_GAP.md section 6.
     *
     * [basePrice] deliberately stays NOT NULL: every competitor in the reference set
     * keeps a non-null parent price and puts the nullability on the cart line instead.
     */
    @ColumnInfo(name = "has_variants", defaultValue = "0")
    val hasVariants: Boolean = false,

    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverCategoryId") @ColumnInfo(name = "server_category_id") val serverCategoryId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L,
    @ColumnInfo(name = "permission_revision_at_creation") val permissionRevisionAtCreation: Long? = null,
    @ColumnInfo(name = "changed_fields") val changedFields: String? = null,

    /**
     * The shop's pin for this dish, set by the owner in web admin and delivered on pull
     * (MenuItemDTO.isFavourite). Server-owned: the till renders it and orders favourites
     * first, but never asserts it on a push — the server restores its own value on every
     * push path, so a star set here could not stick anyway.
     */
    @SerializedName("isFavourite") @ColumnInfo(name = "is_favourite", defaultValue = "0")
    val isFavourite: Boolean = false,

    /**
     * THIS TILL's own shortcut, toggled at the counter and deliberately device-local: it is
     * absent from MenuItemSyncDto so it is never pushed, and MasterSyncProcessor carries it
     * across every rebuild of the row. A cashier's fast-pick list is not a shop setting, and
     * pushing it would put a category-style 403-on-the-whole-batch inside the billing flow.
     */
    @ColumnInfo(name = "local_favourite", defaultValue = "0")
    val localFavourite: Boolean = false
)
