package com.khanabook.lite.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * KOT event types. An immutable event is appended for every kitchen-facing
 * revision of a bill so multi-device print ownership and audit history are
 * preserved (PLAN §6).
 */
object KotEventType {
    const val NEW = "NEW"
    const val ADD = "ADD"
    const val VOID = "VOID"
    /** Crash/ambiguous origin — recorded rather than silently printed. */
    const val UNKNOWN = "UNKNOWN"
}

/**
 * Immutable KOT event, identified by (publicToken, kotRevision).
 *
 * Mirrors the `kot_events` table created in AppDatabase MIGRATION_55_56.
 * The originating device auto-prints its own events; pulled events from other
 * terminals are never auto-printed (enforced in PrintRouter).
 */
@Entity(
    tableName = "kot_events",
    primaryKeys = ["public_token", "kot_revision"],
    indices = [
        Index(value = ["public_token"]),
        Index(value = ["originating_device_id"])
    ]
)
data class KotEventEntity(
    @ColumnInfo(name = "public_token")
    val publicToken: String,
    @ColumnInfo(name = "kot_revision")
    val kotRevision: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "item_snapshot_json")
    val itemSnapshotJson: String,
    @ColumnInfo(name = "originating_device_id")
    val originatingDeviceId: String,
    @ColumnInfo(name = "is_printed")
    val isPrinted: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
