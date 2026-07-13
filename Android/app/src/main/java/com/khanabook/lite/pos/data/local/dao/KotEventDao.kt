package com.khanabook.lite.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khanabook.lite.pos.data.local.entity.KotEventEntity

/**
 * Data access for immutable KOT events (PLAN §6).
 *
 * Events are append-only and keyed by (publicToken, kotRevision); the only
 * mutation permitted is flipping [KotEventEntity.isPrinted] once a ticket has
 * been physically printed.
 */
@Dao
interface KotEventDao {

    /** Append a KOT event. Ignore if the (token, revision) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: KotEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<KotEventEntity>)

    @Query(
        "SELECT * FROM kot_events WHERE public_token = :publicToken ORDER BY created_at ASC"
    )
    suspend fun getEventsForBill(publicToken: String): List<KotEventEntity>

    @Query(
        "UPDATE kot_events SET is_printed = 1 WHERE public_token = :publicToken AND kot_revision = :kotRevision"
    )
    suspend fun markPrinted(publicToken: String, kotRevision: String): Int

    @Query("SELECT COUNT(*) FROM kot_events WHERE public_token = :publicToken")
    suspend fun getEventCountForBill(publicToken: String): Int

    @Query("SELECT COALESCE(MAX(CAST(kot_revision AS INTEGER)), 0) FROM kot_events WHERE public_token = :publicToken")
    suspend fun getMaxRevisionForBill(publicToken: String): Long

    @Query("SELECT * FROM kot_events WHERE public_token = :publicToken AND is_printed = 0 ORDER BY CAST(kot_revision AS INTEGER) DESC LIMIT 1")
    suspend fun getLatestUnprintedEvent(publicToken: String): KotEventEntity?

    @Query("UPDATE kot_events SET is_printed = 1 WHERE public_token = :publicToken AND is_printed = 0")
    suspend fun markUnprintedEventsPrinted(publicToken: String): Int

    @Query(
        "SELECT * FROM kot_events WHERE public_token = :publicToken AND kot_revision = :kotRevision LIMIT 1"
    )
    suspend fun getEvent(publicToken: String, kotRevision: String): KotEventEntity?
}
