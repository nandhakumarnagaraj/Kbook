package com.khanabook.lite.pos.data.local.entity

import androidx.room.*

@Entity(
    tableName = "permission_requests",
    indices = [
        Index(value = ["restaurant_id", "user_id", "status"])
    ]
)
data class PermissionRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "restaurant_id")
    val restaurantId: Long,
    @ColumnInfo(name = "user_id")
    val userId: Long,
    @ColumnInfo(name = "permission_key")
    val permissionKey: String,
    @ColumnInfo(name = "status", defaultValue = "'PENDING'")
    val status: String = "PENDING",
    @ColumnInfo(name = "reason")
    val reason: String? = null,
    @ColumnInfo(name = "requested_at")
    val requestedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    @ColumnInfo(name = "rejection_reason")
    val rejectionReason: String? = null
)
