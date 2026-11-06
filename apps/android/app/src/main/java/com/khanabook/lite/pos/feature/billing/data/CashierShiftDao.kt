package com.khanabook.lite.pos.feature.billing.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CashierShiftDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shift: CashierShiftEntity): Long

    @Query(
        """
        SELECT * FROM local_cashier_shifts
        WHERE restaurant_id = :restaurantId
          AND terminal_id = :terminalId
          AND status = 'ACTIVE'
          AND is_deleted = 0
        ORDER BY opened_at DESC
        LIMIT 1
        """
    )
    suspend fun getActiveShift(restaurantId: Long, terminalId: String): CashierShiftEntity?

    @Query("SELECT * FROM local_cashier_shifts WHERE public_token = :publicToken LIMIT 1")
    suspend fun getByPublicToken(publicToken: String): CashierShiftEntity?

    @Query(
        """
        SELECT * FROM local_cashier_shifts
        WHERE restaurant_id = :restaurantId
          AND business_date = :businessDate
          AND is_deleted = 0
        ORDER BY opened_at ASC
        """
    )
    suspend fun getForBusinessDate(restaurantId: Long, businessDate: String): List<CashierShiftEntity>

    @Query(
        """
        SELECT * FROM local_cashier_shifts
        WHERE restaurant_id = :restaurantId
          AND is_synced = 0
          AND is_deleted = 0
        ORDER BY opened_at ASC
        """
    )
    suspend fun getUnsynced(restaurantId: Long): List<CashierShiftEntity>

    @Query(
        """
        UPDATE local_cashier_shifts
        SET server_id = :serverId, server_updated_at = :serverUpdatedAt, is_synced = 1
        WHERE id = :id
        """
    )
    suspend fun markSynced(id: Long, serverId: Long, serverUpdatedAt: Long)

    /**
     * Seals a shift. Guarded on `status = 'ACTIVE'` so a second close can never overwrite
     * the declared count, the variance or the snapshot. Returns 0 when the shift was
     * already sealed — a normal race between the cashier and the cutoff, not an error.
     */
    @Query(
        """
        UPDATE local_cashier_shifts
        SET closed_at = :closedAt,
            declared_cash = :declaredCash,
            status = :status,
            close_source = :closeSource,
            snapshot_json = :snapshotJson,
            is_synced = 0,
            updated_at = :updatedAt
        WHERE id = :id AND status = 'ACTIVE'
        """
    )
    suspend fun sealShift(
        id: Long,
        closedAt: Long,
        declaredCash: String?,
        status: String,
        closeSource: String?,
        snapshotJson: String?,
        updatedAt: Long
    ): Int

    /**
     * Opens a shift, or returns the one already open for this terminal.
     *
     * Room cannot express the partial unique index `(restaurant_id, terminal_id)` for
     * `status = 'ACTIVE'` (and an index present only in the database fails Room's
     * post-migration validation), so the invariant is enforced here, inside one
     * transaction: read-then-insert cannot interleave with a second caller.
     */
    @Transaction
    suspend fun openShift(shift: CashierShiftEntity): Long {
        require(shift.isOpen) { "A shift must be opened as ACTIVE, not ${shift.status}" }
        val existing = getActiveShift(shift.restaurantId, shift.terminalId)
        if (existing != null) return existing.id
        return insert(shift)
    }
}
