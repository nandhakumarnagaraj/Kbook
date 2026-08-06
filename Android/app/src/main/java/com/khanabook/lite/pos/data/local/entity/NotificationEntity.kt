package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: Long = 0,
    @ColumnInfo(name = "notification_type") val notificationType: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "message") val message: String? = null,
    @ColumnInfo(name = "reference_id") val referenceId: String? = null,
    @ColumnInfo(name = "reference_type") val referenceType: String? = null,
    @ColumnInfo(name = "amount") val amount: String? = null,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
