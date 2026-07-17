package com.khanabook.lite.pos.data.local.dao

import com.khanabook.lite.pos.domain.util.AppConstants

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.entity.SyncQuarantineEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import kotlinx.coroutines.flow.Flow

data class BillIdDuplicateGroup(
    val idValue: String,
    val duplicateCount: Int,
    val sampleBills: String?
)

data class BillIdConflictBill(
    val id: Long,
    val dailyOrderDisplay: String,
    val lifetimeOrderId: Long?,
    val invoiceNumber: String?,
    val orderType: String,
    val orderStatus: String,
    val paymentStatus: String,
    val paymentMode: String,
    val totalAmount: String,
    val createdAt: Long
)

@Dao
interface BillDao {
    @Query("SELECT id, server_id as serverId FROM bills WHERE server_id IS NOT NULL AND is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getAllBillServerIds(restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT id, server_id as serverId FROM bills WHERE id IN (:ids) AND server_id IS NOT NULL AND is_deleted = 0 AND restaurant_id = :restaurantId")
    suspend fun getBillServerIdsByLocalIds(ids: List<Long>, restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT * FROM bills WHERE server_id = :serverId AND restaurant_id = :restaurantId AND is_deleted = 0 LIMIT 1")
    suspend fun getBillByServerId(serverId: Long, restaurantId: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE id = :localId AND device_id = :deviceId AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun getBillByLocalId(localId: Long, deviceId: String, restaurantId: Long): BillEntity?

    @Query("UPDATE bill_items SET bill_id = :targetBillId WHERE bill_id = :sourceBillId AND restaurant_id = :restaurantId")
    suspend fun moveBillItemsToBill(sourceBillId: Long, targetBillId: Long, restaurantId: Long): Int

    @Query("UPDATE bill_payments SET bill_id = :targetBillId WHERE bill_id = :sourceBillId AND restaurant_id = :restaurantId")
    suspend fun moveBillPaymentsToBill(sourceBillId: Long, targetBillId: Long, restaurantId: Long): Int

    @Query("UPDATE OR IGNORE kitchen_print_queue SET bill_id = :targetBillId WHERE bill_id = :sourceBillId AND restaurant_id = :restaurantId")
    suspend fun moveKitchenPrintQueueToBill(sourceBillId: Long, targetBillId: Long, restaurantId: Long): Int

    @Query("""
        UPDATE bills
        SET is_deleted = 1,
            server_id = NULL,
            is_synced = 1,
            sync_status = 'synced',
            sync_failure_reason = NULL,
            sync_failed_at = NULL
        WHERE id = :billId AND restaurant_id = :restaurantId
    """)
    suspend fun hideDuplicateBill(billId: Long, restaurantId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity): Long

    @Update
    suspend fun updateBill(bill: BillEntity)

@Query("""
        SELECT * FROM bills
        WHERE restaurant_id = :restaurantId
          AND order_status = 'draft'
          AND payment_status = 'pending'
          AND is_deleted = 0
          AND record_scope = 'terminal_operational'
          AND record_origin = 'local_created'
          AND (
              current_owner_terminal_id = :terminalId
              OR (current_owner_terminal_id IS NULL AND created_terminal_id = :terminalId)
          )
          AND NOT EXISTS (
              SELECT 1
              FROM bill_payments
              WHERE bill_payments.bill_id = bills.id
                AND bill_payments.restaurant_id = :restaurantId
                AND bill_payments.is_deleted = 0
          )
        ORDER BY updated_at DESC
    """)
fun getActiveDraftBillsFlow(restaurantId: Long, terminalId: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bill_items WHERE bill_id = :billId AND restaurant_id = :restaurantId AND sent_to_kot = 0 AND is_deleted = 0")
    suspend fun getUnsentItemsForBill(billId: Long, restaurantId: Long): List<BillItemEntity>

    @Query("UPDATE bill_items SET sent_to_kot = 1 WHERE id IN (:itemIds) AND restaurant_id = :restaurantId")
    suspend fun markItemsSentToKot(itemIds: List<Long>, restaurantId: Long)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Update
    suspend fun updateBillItem(item: BillItemEntity)

    @Query("DELETE FROM bill_items WHERE id = :id")
    suspend fun deleteBillItemById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillPayments(payments: List<BillPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillPayment(payment: BillPaymentEntity)

    @Query("SELECT * FROM bills WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun getBillById(id: Long, restaurantId: Long): BillEntity?

    // Terminal ownership isolation: a bill that THIS terminal may mutate as an operational
    // record. Returns null for server-imported / marketplace history, other terminals' bills,
    // or anything not explicitly local_created + terminal_operational. Mutable workflows must
    // load bills through this method so a history record can never reach a DAO write.
    @Query("""
        SELECT * FROM bills
        WHERE id = :id AND restaurant_id = :restaurantId AND is_deleted = 0
          AND record_scope = 'terminal_operational' AND record_origin = 'local_created'
          AND (current_owner_terminal_id = :terminalId
               OR (current_owner_terminal_id IS NULL AND created_terminal_id = :terminalId))
        LIMIT 1
    """)
    suspend fun getOperationalBillById(id: Long, restaurantId: Long, terminalId: String): BillEntity?

    // Runtime reconciliation (post-migration / post-activation). The authoritative terminal is
    // only known at runtime, so this corrects the migration's conservative backfill: this
    // terminal's own synced completed bills are re-labelled local_created / terminal_operational,
    // while everything else (including other terminals' pulled bills) stays server_imported
    // history. Idempotent — safe to call after every activation/pull.
    @Query("""
        UPDATE bills
        SET record_origin = 'local_created', record_scope = 'terminal_operational'
        WHERE restaurant_id = :restaurantId AND is_deleted = 0 AND is_synced = 1
          AND created_terminal_id = :terminalId
          AND (source_channel IS NULL OR source_channel NOT IN ('zomato', 'swiggy', 'own_website'))
    """)
    suspend fun reconcileLocalRecordScope(restaurantId: Long, terminalId: String): Int

    // Canonical bill lookup by immutable public_token (see PLAN §4.1).
    @Query("SELECT * FROM bills WHERE public_token = :publicToken AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun getBillByPublicToken(publicToken: String, restaurantId: Long): BillEntity?

    @Query("""
        SELECT COUNT(*) FROM bills
        WHERE restaurant_id = :restaurantId
          AND is_deleted = 0
          AND daily_order_id = :dailyOrderId
          AND created_at BETWEEN :startTime AND :endTime
          AND ((:terminalSeries IS NULL AND terminal_series IS NULL) OR terminal_series = :terminalSeries)
    """)
    suspend fun countActiveBillsByDailyIdAndDate(
        restaurantId: Long,
        dailyOrderId: Long,
        startTime: Long,
        endTime: Long,
        terminalSeries: String?
    ): Int

    @Query("SELECT * FROM bills WHERE id IN (:billIds) AND restaurant_id = :restaurantId")
    suspend fun getBillsByIds(billIds: List<Long>, restaurantId: Long): List<BillEntity>

    @Query("""
        SELECT * FROM bills
        WHERE lifetime_order_id = :id
          AND restaurant_id = :restaurantId
          AND is_deleted = 0
        ORDER BY (SELECT COUNT(*) FROM bill_items WHERE bill_items.bill_id = bills.id AND bill_items.restaurant_id = :restaurantId) DESC,
                 updated_at DESC
        LIMIT 1
    """)
    suspend fun getBillByLifetimeId(id: Long, restaurantId: Long): BillEntity?

    @Query(
            """
            SELECT * FROM bills
            WHERE daily_order_display = :displayId
              AND restaurant_id = :restaurantId
              AND is_deleted = 0
              AND created_terminal_id = :terminalId
              AND created_at BETWEEN :startTime AND :endTime
            ORDER BY (SELECT COUNT(*) FROM bill_items WHERE bill_items.bill_id = bills.id AND bill_items.restaurant_id = :restaurantId) DESC,
                     updated_at DESC
            LIMIT 1
            """
    )
    suspend fun getBillByDailyIdAndDate(displayId: String, startTime: Long, endTime: Long, restaurantId: Long, terminalId: String): BillEntity?

    @Query(
            """
            SELECT * FROM bills
            WHERE daily_order_id = :dailyId
              AND restaurant_id = :restaurantId
              AND is_deleted = 0
              AND created_terminal_id = :terminalId
              AND created_at BETWEEN :startTime AND :endTime
            ORDER BY (SELECT COUNT(*) FROM bill_items WHERE bill_items.bill_id = bills.id AND bill_items.restaurant_id = :restaurantId) DESC,
                     updated_at DESC
            LIMIT 1
            """
    )
    suspend fun getBillByDailyIntIdAndDate(dailyId: Long, startTime: Long, endTime: Long, restaurantId: Long, terminalId: String): BillEntity?

@Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND restaurant_id = :restaurantId
          AND is_deleted = 0
          AND created_terminal_id = :terminalId
          AND record_scope = 'terminal_operational'
          AND record_origin = 'local_created'
          AND NOT EXISTS (
              SELECT 1
              FROM bill_payments
              WHERE bill_payments.bill_id = bills.id
                AND bill_payments.restaurant_id = :restaurantId
                AND bill_payments.is_deleted = 0
          )
    """)
fun getDraftBills(restaurantId: Long, terminalId: String): Flow<List<BillEntity>>

@Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND payment_status = 'pending'
          AND restaurant_id = :restaurantId
          AND owner_user_id = :ownerUserId
          AND created_terminal_id = :terminalId
          AND record_scope = 'terminal_operational'
          AND record_origin = 'local_created'
          AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
          )
        ORDER BY created_at DESC
        LIMIT 1
    """)
suspend fun getLatestPendingOnlineBill(restaurantId: Long, ownerUserId: Long, terminalId: String): BillEntity?

    @Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND payment_status = 'pending'
          AND restaurant_id = :restaurantId
          AND created_terminal_id = :terminalId
          AND record_scope = 'terminal_operational'
          AND record_origin = 'local_created'
          AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
          )
        ORDER BY created_at DESC
        LIMIT 1
    """)
    suspend fun getLatestPendingOnlineBill(restaurantId: Long, terminalId: String): BillEntity?

@Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND payment_status = 'pending'
          AND restaurant_id = :restaurantId
          AND is_deleted = 0
          AND created_terminal_id = :terminalId
          AND record_scope = 'terminal_operational'
          AND record_origin = 'local_created'
          AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
          )
        ORDER BY created_at DESC
    """)
fun getPendingOnlineBillsFlow(restaurantId: Long, terminalId: String): Flow<List<BillEntity>>

    @Query("UPDATE bills SET order_status = :status WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun updateOrderStatus(id: Long, status: String, restaurantId: Long)

    @Query("UPDATE bills SET payment_mode = :mode WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun updatePaymentMode(id: Long, mode: String, restaurantId: Long)

    @Query("UPDATE bills SET payment_status = :status WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun updatePaymentStatus(id: Long, status: String, restaurantId: Long)

    @Query("UPDATE bills SET order_status = 'cancelled', cancel_reason = :reason, is_synced = 0, updated_at = :updatedAt WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun cancelBill(id: Long, reason: String, updatedAt: Long, restaurantId: Long)

    @Query("""
        UPDATE bills SET order_status = 'cancelled', payment_status = 'failed',
        cancel_reason = :reason, is_synced = 0, updated_at = :updatedAt
        WHERE order_status = 'draft' AND payment_status = 'pending'
        AND restaurant_id = :restaurantId
        AND created_terminal_id = :terminalId
        AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
        )
        AND NOT EXISTS (
            SELECT 1 FROM bill_items
            WHERE bill_items.bill_id = bills.id
              AND bill_items.restaurant_id = :restaurantId
              AND bill_items.is_deleted = 0
        )
    """)
    suspend fun cancelStalePendingOnlineDrafts(reason: String, updatedAt: Long, restaurantId: Long, terminalId: String): Int

    @Query(
            "SELECT * FROM bills WHERE created_at BETWEEN :startMillis AND :endMillis AND is_deleted = 0 AND restaurant_id = :restaurantId AND created_terminal_id = :terminalId ORDER BY created_at DESC"
    )
    fun getBillsByDateRange(startMillis: Long, endMillis: Long, restaurantId: Long, terminalId: String): Flow<List<BillEntity>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun getBillWithItemsById(id: Long, restaurantId: Long): BillWithItems?

    // Canonical lookup by the GST invoice number (e.g. "26A1-000042"). Used by the
    // Invoice-No search field. Legacy bills (numeric INV lookup) go through getBillByLifetimeNo.
    @Transaction
    @Query("""
        SELECT * FROM bills
        WHERE invoice_number = :invoiceNumber
          AND restaurant_id = :restaurantId
          AND is_deleted = 0
          AND created_terminal_id = :terminalId
        ORDER BY (SELECT COUNT(*) FROM bill_items WHERE bill_items.bill_id = bills.id AND bill_items.restaurant_id = :restaurantId) DESC,
                 updated_at DESC
        LIMIT 1
    """)
    suspend fun getBillWithItemsByInvoiceNumber(invoiceNumber: String, restaurantId: Long, terminalId: String): BillWithItems?

    @Transaction
    suspend fun insertFullBill(
            bill: BillEntity,
            items: List<BillItemEntity>,
            payments: List<BillPaymentEntity>
    ): Long {
        val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
        val orderDate = java.time.Instant.ofEpochMilli(bill.createdAt).atZone(zoneId).toLocalDate()
        val startTime = orderDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endTime = orderDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        if (countActiveBillsByDailyIdAndDate(bill.restaurantId, bill.dailyOrderId, startTime, endTime, bill.terminalSeries) > 0) {
            throw android.database.sqlite.SQLiteConstraintException(
                "Duplicate order id #${bill.dailyOrderDisplay}. Please repair counters from Sync Center and try again."
            )
        }
        val billId = insertBill(bill)
        val itemsWithId = items.map {
            it.copy(
                billId = billId,
                restaurantId = bill.restaurantId,
                deviceId = bill.deviceId
            )
        }
        val paymentsWithId = payments.map {
            it.copy(
                billId = billId,
                restaurantId = bill.restaurantId,
                deviceId = bill.deviceId,
                terminalId = bill.terminalId,
                billPublicToken = bill.publicToken,
                operationId = it.operationId ?: bill.operationId,
                syncStatus = if (it.isSynced) "synced" else "pending"
            )
        }
        insertBillItems(itemsWithId)
        insertBillPayments(paymentsWithId)
        return billId
    }

    @Transaction
    suspend fun settleDraftBill(
        bill: BillEntity,
        payments: List<BillPaymentEntity>
    ) {
        val paymentsWithId = payments.map {
            it.copy(
                billId = bill.id,
                restaurantId = bill.restaurantId,
                deviceId = bill.deviceId,
                terminalId = bill.terminalId,
                billPublicToken = bill.publicToken,
                operationId = it.operationId ?: bill.operationId,
                syncStatus = if (it.isSynced) "synced" else "pending"
            )
        }
        insertBillPayments(paymentsWithId)
        updateBill(bill)
    }

    // Duplicate detection keys on the GST invoice_number, not the legacy lifetime_order_id.
    // New bills leave lifetime_order_id NULL (SQLite groups all NULLs together, which would
    // falsely flag every new bill as a duplicate), so we ignore NULL/blank invoice numbers.
    @Query("""
        SELECT invoice_number AS idValue,
               COUNT(*) AS duplicateCount,
               GROUP_CONCAT('Local ' || id || ' - ' || daily_order_display || '/' || invoice_number || ' - ' || order_type || ' - ' || order_status, ', ') AS sampleBills
        FROM bills
        WHERE restaurant_id = :restaurantId
          AND is_deleted = 0
          AND invoice_number IS NOT NULL
          AND invoice_number != ''
        GROUP BY invoice_number
        HAVING COUNT(*) > 1
        ORDER BY duplicateCount DESC, invoice_number DESC
    """)
    suspend fun getDuplicateInvoiceNumberGroups(restaurantId: Long): List<BillIdDuplicateGroup>

    @Query("""
        SELECT DATE(created_at / 1000, 'unixepoch', 'localtime') || ' #' || daily_order_id
               || CASE WHEN terminal_series IS NULL OR terminal_series = '' THEN '' ELSE '/' || terminal_series END AS idValue,
               COUNT(*) AS duplicateCount,
               GROUP_CONCAT('Local ' || id || ' - ' || daily_order_display || '/' ||
                   COALESCE(invoice_number, CASE WHEN lifetime_order_id IS NULL THEN 'legacy' ELSE 'INV' || lifetime_order_id END) ||
                   ' - ' || order_type || ' - ' || order_status, ', ') AS sampleBills
        FROM bills
        WHERE restaurant_id = :restaurantId
          AND is_deleted = 0
        GROUP BY DATE(created_at / 1000, 'unixepoch', 'localtime'), daily_order_id, COALESCE(terminal_series, '')
        HAVING COUNT(*) > 1
        ORDER BY duplicateCount DESC, idValue DESC
    """)
    suspend fun getDuplicateDailyOrderGroups(restaurantId: Long): List<BillIdDuplicateGroup>

    @Query("""
        SELECT id,
               daily_order_display AS dailyOrderDisplay,
               lifetime_order_id AS lifetimeOrderId,
               invoice_number AS invoiceNumber,
               order_type AS orderType,
               order_status AS orderStatus,
               payment_status AS paymentStatus,
               payment_mode AS paymentMode,
               total_amount AS totalAmount,
               created_at AS createdAt
        FROM bills
        WHERE restaurant_id = :restaurantId
          AND is_deleted = 0
          AND (
            (invoice_number IS NOT NULL AND invoice_number != '' AND invoice_number IN (
                SELECT invoice_number
                FROM bills
                WHERE restaurant_id = :restaurantId AND is_deleted = 0
                  AND invoice_number IS NOT NULL AND invoice_number != ''
                GROUP BY invoice_number
                HAVING COUNT(*) > 1
            ))
            OR EXISTS (
                SELECT 1
                FROM bills other
                WHERE other.restaurant_id = bills.restaurant_id
                  AND other.is_deleted = 0
                  AND other.daily_order_id = bills.daily_order_id
                  AND DATE(other.created_at / 1000, 'unixepoch', 'localtime') = DATE(bills.created_at / 1000, 'unixepoch', 'localtime')
                  AND COALESCE(other.terminal_series, '') = COALESCE(bills.terminal_series, '')
                GROUP BY DATE(other.created_at / 1000, 'unixepoch', 'localtime'), other.daily_order_id, COALESCE(other.terminal_series, '')
                HAVING COUNT(*) > 1
            )
          )
        ORDER BY created_at DESC, invoice_number DESC, id DESC
    """)
    suspend fun getDuplicateIdConflictBills(restaurantId: Long): List<BillIdConflictBill>

    @Query("SELECT COALESCE(MAX(lifetime_order_id), 0) FROM bills WHERE restaurant_id = :restaurantId AND is_deleted = 0")
    suspend fun getMaxLifetimeOrderId(restaurantId: Long): Long

    @Query("""
        SELECT COALESCE(MAX(daily_order_id), 0)
        FROM bills
        WHERE restaurant_id = :restaurantId
          AND device_id = :deviceId
          AND is_deleted = 0
          AND created_at BETWEEN :startTime AND :endTime
    """)
    suspend fun getMaxDailyOrderIdBetween(restaurantId: Long, deviceId: String, startTime: Long, endTime: Long): Long

    @Query("""
        SELECT COALESCE(MAX(daily_order_id), 0)
        FROM bills
        WHERE restaurant_id = :restaurantId
          AND is_deleted = 0
          AND created_at BETWEEN :startTime AND :endTime
          AND (created_terminal_id = :terminalId OR terminal_id = :terminalId)
    """)
    suspend fun getMaxDailyOrderIdForTerminalToday(restaurantId: Long, terminalId: String, startTime: Long, endTime: Long): Long

    // Highest invoice sequence allocated within a terminal's invoice series.
    // Filtered by invoice_series (the exact unique-key component, e.g. "25A") rather
    // than the looser terminal_series + financial_year pair, so a server-pulled bill
    // under the same series is always counted and local allocation never reuses an
    // already-allocated sequence (prevents ux_bills_restaurant_invoice_series_active
    // unique violations / 409 push loops after a pull).
    @Query("""
        SELECT COALESCE(MAX(invoice_sequence), 0)
        FROM bills
        WHERE restaurant_id = :restaurantId
          AND invoice_series = :invoiceSeries
          AND is_deleted = 0
    """)
    suspend fun getMaxInvoiceSequence(restaurantId: Long, invoiceSeries: String): Long

    // ── Sync reconciliation ────────────────────────────────────────────────────

    /**
     * Marks any bill that has a server_id assigned (from a successful push
     * round-trip or from a server pull) as synced. Catches bills where the push
     * network response was lost but the server actually saved the record.
     */
    @Query("UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL WHERE is_synced = 0 AND server_id IS NOT NULL AND updated_at <= server_updated_at AND restaurant_id = :restaurantId")
    suspend fun reconcileServerAcknowledgedBills(restaurantId: Long): Int

    /**
     * After a pull, the server returns bills with their server-assigned IDs.
     * For bills created on THIS device, the original local row (with device-local pk)
     * may still be isSynced=0 because the pull upserted a NEW row keyed by the
     * server ID. This query finds those orphaned local rows by matching on the
     * immutable public_token (canonical bill identity) and marks them synced.
     *
     * IMPORTANT: Only mark synced when updated_at <= server_updated_at, meaning the
     * server already has the latest version of this bill. If the local bill was modified
     * after the server last updated it (e.g., order_status changed to 'completed' offline),
     * we must NOT mark it synced — otherwise the pending local change is silently dropped.
     */
    @Query("""
        UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL
        WHERE is_synced = 0
          AND restaurant_id = :restaurantId
          AND public_token IN (:publicTokens)
          AND updated_at <= server_updated_at
    """)
    suspend fun markBillsSyncedByPublicTokens(restaurantId: Long, publicTokens: List<String>): Int

    @Query("""
        UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL
        WHERE is_synced = 0
          AND restaurant_id = :restaurantId
          AND public_token = :publicToken
          AND updated_at <= :serverUpdatedAt
    """)
    suspend fun markBillSyncedByPublicToken(
        restaurantId: Long,
        publicToken: String,
        serverUpdatedAt: Long
    ): Int

    // ── Unsynced queries ───────────────────────────────────────────────────────

    @Query("SELECT * FROM bills WHERE is_synced = 0 AND restaurant_id = :restaurantId AND sync_status != 'failed_permanent'")
    suspend fun getUnsyncedBills(restaurantId: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE is_synced = 0 AND owner_user_id = :userId AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedBillsForUser(userId: Long, restaurantId: Long): List<BillEntity>

    @Query("""
        SELECT bi.* FROM bill_items bi
        INNER JOIN bills b ON bi.bill_id = b.id
        WHERE bi.is_synced = 0 AND b.owner_user_id = :userId AND bi.restaurant_id = :restaurantId AND b.restaurant_id = :restaurantId
    """)
    suspend fun getUnsyncedBillItemsForUser(userId: Long, restaurantId: Long): List<BillItemEntity>

    @Query("""
        SELECT bp.* FROM bill_payments bp
        INNER JOIN bills b ON bp.bill_id = b.id
        WHERE bp.is_synced = 0 AND b.owner_user_id = :userId AND bp.restaurant_id = :restaurantId AND b.restaurant_id = :restaurantId
    """)
    suspend fun getUnsyncedBillPaymentsForUser(userId: Long, restaurantId: Long): List<BillPaymentEntity>

    @Query("SELECT COUNT(*) FROM bills WHERE is_synced = 0 AND owner_user_id = :userId AND restaurant_id = :restaurantId")
    fun getUnsyncedCountForUser(userId: Long, restaurantId: Long): Flow<Int>

    /**
     * After a pull, the server returns bills with the ORIGINAL device-local IDs
     * in their `localId` field. For rows where isMyDevice=false (deviceId changed,
     * reinstall, etc.), the upsert uses the serverId as the Room primary key, so
     * the ORIGINAL local row (with the old device-local ID) remains isSynced=false.
     *
     * This query finds those orphaned rows by matching on (device_id, local_id)
     * and marks them synced. This is more reliable than lifetimeOrderId because
     * localId is always the original device-local primary key.
     *
     * IMPORTANT: Only mark synced when updated_at <= server_updated_at, meaning the
     * server already has the latest version of this bill. If the local bill was modified
     * after the server last updated it (e.g., order_status changed to 'completed' offline),
     * we must NOT mark it synced — otherwise the pending local change is silently dropped
     * and the draft status on the server is never corrected.
     */
    @Query("""
        UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL
        WHERE is_synced = 0
          AND device_id = :deviceId
          AND restaurant_id = :restaurantId
          AND id IN (:localIds)
          AND updated_at <= server_updated_at
    """)
    suspend fun markBillsSyncedByDeviceIdAndLocalIds(deviceId: String, restaurantId: Long, localIds: List<Long>): Int

    @Query("UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL WHERE id IN (:billIds) AND restaurant_id = :restaurantId")
    suspend fun markBillsAsSynced(billIds: List<Long>, restaurantId: Long)

    @Query("""
        UPDATE bills
        SET sync_status = 'failed_permanent',
            sync_failure_reason = :reason,
            sync_failed_at = :failedAt
        WHERE id = :billId
          AND restaurant_id = :restaurantId
          AND is_synced = 0
    """)
    suspend fun markBillSyncFailedPermanently(
        billId: Long,
        restaurantId: Long,
        reason: String,
        failedAt: Long
    ): Int

    @Query("""
        UPDATE bills
        SET sync_status = 'pending',
            sync_failure_reason = NULL,
            sync_failed_at = NULL
        WHERE id = :billId
          AND restaurant_id = :restaurantId
          AND is_synced = 0
    """)
    suspend fun retryFailedBillSync(billId: Long, restaurantId: Long): Int

    @Query("SELECT * FROM bills WHERE sync_status = 'failed_permanent' AND restaurant_id = :restaurantId ORDER BY sync_failed_at DESC")
    suspend fun getPermanentlyFailedBills(restaurantId: Long): List<BillEntity>

    @Query("UPDATE bills SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Query("SELECT * FROM bills WHERE is_synced = 0 AND restaurant_id = :restaurantId AND sync_status != 'failed_permanent' LIMIT :limit")
    suspend fun getUnsyncedBillsPaged(restaurantId: Long, limit: Int): List<BillEntity>

    @Transaction
    suspend fun insertSyncedBills(bills: List<BillEntity>) {
        for (bill in bills) {
            val localByDeviceId = getBillByLocalId(bill.id, bill.deviceId, bill.restaurantId)
            val localByServerId = bill.serverId?.let { getBillByServerId(it, bill.restaurantId) }
            val localBill = localByDeviceId ?: localByServerId

            if (
                localByDeviceId != null &&
                localByServerId != null &&
                localByDeviceId.id != localByServerId.id
            ) {
                moveBillItemsToBill(localByServerId.id, localByDeviceId.id, bill.restaurantId)
                moveBillPaymentsToBill(localByServerId.id, localByDeviceId.id, bill.restaurantId)
                moveKitchenPrintQueueToBill(localByServerId.id, localByDeviceId.id, bill.restaurantId)
                hideDuplicateBill(localByServerId.id, bill.restaurantId)
            }

            if (localBill == null) {
                // Not present locally at all, insert it
                insertBill(bill)
            } else {
                // Present locally. Check if we should overwrite it.
                // We overwrite if local is already synced, OR if remote updatedAt is strictly newer.
                if (localBill.isSynced || (bill.updatedAt > localBill.updatedAt)) {
                    // Update the local bill but preserve local id if matching
                    val updatedLocalBill = bill.copy(id = localBill.id)
                    insertBill(updatedLocalBill)
                } else {
                    // Preserve newer local fields, but still merge server identity from the pull.
                    // Bill items link through serverBillId -> bills.server_id; without this repair
                    // pulled items can be skipped and existing bills open with no item rows.
                    val repairedLocalBill = localBill.copy(
                        serverId = localBill.serverId ?: bill.serverId,
                        serverUpdatedAt = maxOf(localBill.serverUpdatedAt, bill.serverUpdatedAt),
                        publicToken = localBill.publicToken ?: bill.publicToken
                    )
                    if (repairedLocalBill != localBill) {
                        insertBill(repairedLocalBill)
                    }
                }
            }
        }
    }

    @Query("SELECT COUNT(*) FROM bills WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedCountOnce(restaurantId: Long): Int

    @Query("SELECT COUNT(*) FROM bills WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    fun getUnsyncedCount(restaurantId: Long): Flow<Int>

    @Query("SELECT * FROM bill_items WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedBillItems(restaurantId: Long): List<BillItemEntity>

    @Query("""
        SELECT bi.* FROM bill_items bi
        INNER JOIN bills b ON bi.bill_id = b.id
        WHERE bi.is_synced = 0
          AND bi.restaurant_id = :restaurantId
          AND b.restaurant_id = :restaurantId
          AND (b.is_synced = 1 OR b.server_id IS NOT NULL)
    """)
    suspend fun getUnsyncedBillItemsWithSyncedParent(restaurantId: Long): List<BillItemEntity>

    @Query("SELECT * FROM bill_items WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun getBillItemsByIds(ids: List<Long>, restaurantId: Long): List<BillItemEntity>

    @Query("UPDATE bill_items SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markBillItemsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE bill_items SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateBillItemServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Query("UPDATE bill_items SET server_bill_id = :serverBillId WHERE bill_id = :billLocalId AND restaurant_id = :restaurantId AND server_bill_id IS NULL")
    suspend fun updateBillItemsServerBillIdByBillLocalId(billLocalId: Long, serverBillId: Long, restaurantId: Long): Int

    @Query("""
        UPDATE bill_items
        SET server_bill_id = (
            SELECT b.server_id
            FROM bills b
            WHERE b.id = bill_items.bill_id
              AND b.restaurant_id = :restaurantId
        )
        WHERE restaurant_id = :restaurantId
          AND server_bill_id IS NULL
          AND EXISTS (
              SELECT 1
              FROM bills b
              WHERE b.id = bill_items.bill_id
                AND b.restaurant_id = :restaurantId
                AND b.server_id IS NOT NULL
          )
    """)
    suspend fun backfillBillItemServerBillIds(restaurantId: Long): Int

    @Query("UPDATE bill_items SET server_menu_item_id = :serverMenuItemId WHERE menu_item_id = :menuItemLocalId AND restaurant_id = :restaurantId AND server_menu_item_id IS NULL")
    suspend fun updateBillItemsServerMenuItemIdByMenuItemLocalId(menuItemLocalId: Long, serverMenuItemId: Long, restaurantId: Long): Int

    @Query("UPDATE bill_items SET server_variant_id = :serverVariantId WHERE variant_id = :variantLocalId AND restaurant_id = :restaurantId AND server_variant_id IS NULL")
    suspend fun updateBillItemsServerVariantIdByVariantLocalId(variantLocalId: Long, serverVariantId: Long, restaurantId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBillItems(items: List<BillItemEntity>)

    @Query("SELECT COUNT(*) FROM bill_items WHERE restaurant_id = :restaurantId")
    suspend fun countBillItems(restaurantId: Long): Int

    @Query("SELECT COUNT(DISTINCT bill_id) FROM bill_items WHERE restaurant_id = :restaurantId")
    suspend fun countBillsWithItems(restaurantId: Long): Int

    @Query("SELECT COUNT(*) FROM bills WHERE restaurant_id = :restaurantId AND is_deleted = 0")
    suspend fun countActiveBills(restaurantId: Long): Int

    @Query("DELETE FROM bill_items WHERE is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteAllSyncedBillItems(restaurantId: Long)

    @Query("DELETE FROM bill_items WHERE bill_id IN (:billIds) AND is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteSyncedBillItemsForBills(billIds: List<Long>, restaurantId: Long)

    @Query("DELETE FROM bill_items WHERE server_id IN (:serverIds) AND is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteSyncedBillItemsByServerIds(serverIds: List<Long>, restaurantId: Long)

    @Query("SELECT * FROM bill_payments WHERE is_synced = 0 AND restaurant_id = :restaurantId")
    suspend fun getUnsyncedBillPayments(restaurantId: Long): List<BillPaymentEntity>

    @Query("""
        SELECT bp.* FROM bill_payments bp
        INNER JOIN bills b ON bp.bill_id = b.id
        WHERE bp.is_synced = 0
          AND bp.restaurant_id = :restaurantId
          AND b.restaurant_id = :restaurantId
          AND (b.is_synced = 1 OR b.server_id IS NOT NULL)
    """)
    suspend fun getUnsyncedBillPaymentsWithSyncedParent(restaurantId: Long): List<BillPaymentEntity>

    @Query("SELECT * FROM bill_payments WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun getBillPaymentsByIds(ids: List<Long>, restaurantId: Long): List<BillPaymentEntity>

    @Query("UPDATE bill_payments SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markBillPaymentsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE bill_payments SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateBillPaymentServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Query("UPDATE bill_payments SET server_bill_id = :serverBillId WHERE bill_id = :billLocalId AND restaurant_id = :restaurantId AND server_bill_id IS NULL")
    suspend fun updateBillPaymentsServerBillIdByBillLocalId(billLocalId: Long, serverBillId: Long, restaurantId: Long): Int

    @Query("""
        UPDATE bill_payments
        SET server_bill_id = (
            SELECT b.server_id
            FROM bills b
            WHERE b.id = bill_payments.bill_id
              AND b.restaurant_id = :restaurantId
        )
        WHERE restaurant_id = :restaurantId
          AND server_bill_id IS NULL
          AND EXISTS (
              SELECT 1
              FROM bills b
              WHERE b.id = bill_payments.bill_id
                AND b.restaurant_id = :restaurantId
                AND b.server_id IS NOT NULL
          )
    """)
    suspend fun backfillBillPaymentServerBillIds(restaurantId: Long): Int

    @Query("""
        SELECT item_name as itemName, SUM(quantity) as quantitySold, SUM(item_total) as revenue
        FROM bill_items
        INNER JOIN bills ON bill_items.bill_id = bills.id
        WHERE bills.order_status = 'completed'
          AND bills.is_deleted = 0
          AND bills.restaurant_id = :restaurantId
          AND bills.created_terminal_id = :terminalId
          AND bills.created_at BETWEEN :startMillis AND :endMillis
        GROUP BY item_name
        ORDER BY quantitySold DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int, restaurantId: Long, terminalId: String): List<com.khanabook.lite.pos.domain.model.TopSellingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBillPayments(payments: List<BillPaymentEntity>)

    @Query("SELECT * FROM sync_quarantine_records WHERE restaurant_id = :restaurantId ORDER BY quarantined_at DESC, id DESC")
    fun getSyncQuarantineRecordsFlow(restaurantId: Long): Flow<List<SyncQuarantineEntity>>

    @Query("SELECT COUNT(*) FROM sync_quarantine_records WHERE restaurant_id = :restaurantId")
    fun getSyncQuarantineCountFlow(restaurantId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncQuarantineRecord(record: SyncQuarantineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncQuarantineRecords(records: List<SyncQuarantineEntity>)

    @Query("DELETE FROM bill_payments WHERE is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteAllSyncedBillPayments(restaurantId: Long)

    @Query("DELETE FROM bill_payments WHERE bill_id IN (:billIds) AND is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteSyncedBillPaymentsForBills(billIds: List<Long>, restaurantId: Long)

    @Query("DELETE FROM bill_payments WHERE server_id IN (:serverIds) AND is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteSyncedBillPaymentsByServerIds(serverIds: List<Long>, restaurantId: Long)

    @Query("SELECT * FROM bills WHERE customer_whatsapp IS NOT NULL AND customer_whatsapp != '' AND order_type = 'takeaway' AND order_status = 'completed' AND is_deleted = 0 AND restaurant_id = :restaurantId AND created_terminal_id = :terminalId ORDER BY created_at DESC LIMIT 20")
    suspend fun getRecentBillsWithCustomers(restaurantId: Long, terminalId: String): List<BillEntity>

    @Query("SELECT * FROM bills WHERE customer_name IS NOT NULL AND customer_name != '' AND order_type = 'dine_in' AND order_status = 'completed' AND is_deleted = 0 AND restaurant_id = :restaurantId AND created_terminal_id = :terminalId ORDER BY created_at DESC LIMIT 20")
    suspend fun getRecentDineInBillsWithCustomers(restaurantId: Long, terminalId: String): List<BillEntity>

    @Query("SELECT * FROM bills WHERE lifetime_order_id = :lifetimeNo AND restaurant_id = :restaurantId AND created_terminal_id = :terminalId AND is_deleted = 0 LIMIT 1")
    suspend fun getBillByLifetimeNo(lifetimeNo: Long, restaurantId: Long, terminalId: String): BillEntity?

    @Query(
        """
        SELECT b.* FROM bills b
        INNER JOIN kitchen_print_queue kpq ON b.id = kpq.bill_id
        WHERE kpq.dispatch_status != 'SENT'
          AND b.restaurant_id = :restaurantId
          AND b.created_terminal_id = :terminalId
        ORDER BY b.created_at DESC
        """
    )
    suspend fun getBillsWithPendingKds(restaurantId: Long, terminalId: String): List<BillEntity>
}
