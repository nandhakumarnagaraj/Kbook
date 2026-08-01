package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "printer_profiles",
    indices = [Index(value = ["restaurant_id", "role"], unique = true)]
)
data class PrinterProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,
    @ColumnInfo(name = "restaurant_id", defaultValue = "0")
    val restaurantId: Long = 0,
    val name: String,
    @ColumnInfo(name = "mac_address")
    val macAddress: String,
    @ColumnInfo(name = "connection_type", defaultValue = "'BLUETOOTH'")
    val connectionType: String = "BLUETOOTH",
    @ColumnInfo(name = "host")
    val host: String? = null,
    @ColumnInfo(name = "port", defaultValue = "9100")
    val port: Int = 9100,
    val enabled: Boolean = true,
    @ColumnInfo(name = "auto_print")
    val autoPrint: Boolean = true,
    @ColumnInfo(name = "paper_size")
    val paperSize: String = "58mm",
    @ColumnInfo(name = "include_logo")
    val includeLogo: Boolean = true,
    val copies: Int = 1,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
