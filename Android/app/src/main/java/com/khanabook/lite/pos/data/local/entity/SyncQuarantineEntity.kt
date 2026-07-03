package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_quarantine_records",
    indices = [
        Index(value = ["restaurant_id"]),
        Index(value = ["restaurant_id", "parent_bill_id"]),
        Index(value = ["restaurant_id", "child_entity_type", "child_local_id"], unique = true)
    ]
)
data class SyncQuarantineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "restaurant_id", defaultValue = "0")
    val restaurantId: Long = 0,
    @ColumnInfo(name = "parent_bill_id")
    val parentBillId: Long,
    @ColumnInfo(name = "parent_bill_display")
    val parentBillDisplay: String? = null,
    @ColumnInfo(name = "child_entity_type")
    val childEntityType: String,
    @ColumnInfo(name = "child_local_id")
    val childLocalId: Long,
    @ColumnInfo(name = "child_display_name")
    val childDisplayName: String? = null,
    @ColumnInfo(name = "child_summary")
    val childSummary: String? = null,
    @ColumnInfo(name = "child_snapshot_json")
    val childSnapshotJson: String? = null,
    @ColumnInfo(name = "sync_failure_reason")
    val syncFailureReason: String? = null,
    @ColumnInfo(name = "quarantined_at", defaultValue = "0")
    val quarantinedAt: Long = System.currentTimeMillis()
)
