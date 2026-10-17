package com.khanabook.lite.pos.feature.menu.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A dish the pull could not move, because the category the server put it in had not reached this
 * device yet.
 *
 * The pull never guesses a placement (a raw server category id used as a local id lands the dish
 * in a category that merely shares that number), so such a row keeps the category it already has
 * and the intended destination is parked here instead. The next pull that has the category moves
 * the row and deletes the repair — see `MasterSyncProcessor.applyPendingCategoryRepairs`.
 *
 * Deliberately a table of its own rather than a column on `menu_items`:
 * `menu_items.server_category_id` cannot carry this, because the push payload sends that column
 * to the server and the server trusts it as the item's authoritative category — a stale value
 * there would revert a move made on a terminal.
 */
@Entity(
    tableName = "menu_item_category_repairs",
    indices = [Index(value = ["restaurant_id", "item_local_id"], unique = true)]
)
data class MenuItemCategoryRepairEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "restaurant_id", defaultValue = "0")
    val restaurantId: Long = 0,
    @ColumnInfo(name = "item_local_id")
    val itemLocalId: Long,
    @ColumnInfo(name = "destination_server_category_id")
    val destinationServerCategoryId: Long,
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)
