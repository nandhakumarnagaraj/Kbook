package com.khanabook.lite.pos.data.local.entity

import androidx.room.*

@Entity(
    tableName = "staff_permissions",
    indices = [
        Index(value = ["restaurant_id", "user_id"]),
        Index(value = ["restaurant_id", "user_id", "permission_key"], unique = true)
    ]
)
data class StaffPermissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "restaurant_id")
    val restaurantId: Long,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    @ColumnInfo(name = "permission_key")
    val permissionKey: String,
    @ColumnInfo(name = "granted", defaultValue = "1")
    val granted: Boolean = true,
    @ColumnInfo(name = "granted_at")
    val grantedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
