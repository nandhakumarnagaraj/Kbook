package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "printer_profiles",
    indices = [Index(value = ["role"], unique = true)]
)
data class PrinterProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,
    val name: String,
    @ColumnInfo(name = "mac_address")
    val macAddress: String,
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
