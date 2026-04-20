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
    @ColumnInfo(name = "printer_mac")
    val printerMac: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "dispatch_status")
    val dispatchStatus: String = KitchenPrintDispatchStatus.PENDING,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
