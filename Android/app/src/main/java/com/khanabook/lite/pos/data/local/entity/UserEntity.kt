package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "users")
data class UserEntity(

    @SerializedName("localId") @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "login_id")
    val loginId: String? = null,

    @ColumnInfo(name = "google_email")
    val googleEmail: String? = null,

    @ColumnInfo(name = "auth_provider", defaultValue = "'PHONE'")
    val authProvider: String = "PHONE",

    @ColumnInfo(name = "whatsapp_number")
    val whatsappNumber: String? = null,

    @ColumnInfo(name = "role", defaultValue = "'OWNER'")
    val role: String = "OWNER",

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "restaurant_id", defaultValue = "0") val restaurantId: Long = 0,
    @ColumnInfo(name = "device_id", defaultValue = "''") val deviceId: String = "",
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerializedName("serverId") @ColumnInfo(name = "server_id") val serverId: Long? = null,
    @SerializedName("serverUpdatedAt") @ColumnInfo(name = "server_updated_at", defaultValue = "0") val serverUpdatedAt: Long = 0L
)
