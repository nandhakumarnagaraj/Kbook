package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object KitchenPrintDispatchStatus {
    const val PENDING = "pending"
    const val RETRYING = "retrying"
    const val SENT = "sent"
    const val FAILED = "failed"
}

@Entity(
    tableName = "kitchen_print_queue",
    indices = [Index(value = ["bill_id", "printer_mac"], unique = true)]
)
data class KitchenPrintQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "bill_id")
    val billId: Long,
    @ColumnInfo(name = "restaurant_id", defaultValue = "0")
    val restaurantId: Long = 0,
    @ColumnInfo(name = "terminal_id", defaultValue = "NULL")
    val terminalId: String? = null,
    @ColumnInfo(name = "device_id", defaultValue = "NULL")
    val deviceId: String? = null,
    @ColumnInfo(name = "printer_mac")
    val printerMac: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "dispatch_status")
    val dispatchStatus: String = KitchenPrintDispatchStatus.PENDING,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "public_token")
    val publicToken: String? = null,
    @ColumnInfo(name = "bill_public_token", defaultValue = "NULL")
    val billPublicToken: String? = null,
    @ColumnInfo(name = "print_event_token", defaultValue = "NULL")
    val printEventToken: String? = null,
    @ColumnInfo(name = "kot_revision")
    val kotRevision: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
