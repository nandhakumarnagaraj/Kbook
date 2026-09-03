package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent cache of the current user's granted permissions + authorization
 * revision, so both survive process death while offline (P1). The in-memory
 * StateFlow in PermissionManager remains the fast path; this table is only read
 * once on cold start and written on every sync update.
 *
 * Single logical row per user. `grantedCsv` is a comma-separated list of
 * permission keys (keys never contain commas — they are dot-namespaced).
 */
@Entity(tableName = "permission_cache")
data class PermissionCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: Long,
    @ColumnInfo(name = "granted_csv")
    val grantedCsv: String,
    @ColumnInfo(name = "permission_revision")
    val permissionRevision: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
