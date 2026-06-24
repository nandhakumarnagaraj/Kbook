package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT id, server_id as serverId FROM bills WHERE server_id IS NOT NULL AND restaurant_id = :restaurantId")
    suspend fun getAllBillServerIds(restaurantId: Long): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT * FROM bills WHERE server_id = :serverId LIMIT 1")
    suspend fun getBillByServerId(serverId: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE id = :localId AND device_id = :deviceId AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun getBillByLocalId(localId: Long, deviceId: String, restaurantId: Long): BillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity): Long

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillPayments(payments: List<BillPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillPayment(payment: BillPaymentEntity)

    @Query("SELECT * FROM bills WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun getBillById(id: Long, restaurantId: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE lifetime_order_id = :id AND restaurant_id = :restaurantId")
    suspend fun getBillByLifetimeId(id: Long, restaurantId: Long): BillEntity?

    @Query(
            "SELECT * FROM bills WHERE daily_order_display = :displayId AND restaurant_id = :restaurantId AND created_at BETWEEN :startTime AND :endTime"
    )
    suspend fun getBillByDailyIdAndDate(displayId: String, startTime: Long, endTime: Long, restaurantId: Long): BillEntity?

    @Query(
            "SELECT * FROM bills WHERE daily_order_id = :dailyId AND restaurant_id = :restaurantId AND created_at BETWEEN :startTime AND :endTime"
    )
    suspend fun getBillByDailyIntIdAndDate(dailyId: Long, startTime: Long, endTime: Long, restaurantId: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE order_status = 'draft' AND restaurant_id = :restaurantId")
    fun getDraftBills(restaurantId: Long): Flow<List<BillEntity>>

    @Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND payment_status = 'pending'
          AND restaurant_id = :restaurantId
          AND owner_user_id = :ownerUserId
          AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
          )
        ORDER BY created_at DESC
        LIMIT 1
    """)
    suspend fun getLatestPendingOnlineBill(restaurantId: Long, ownerUserId: Long): BillEntity?

    @Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND payment_status = 'pending'
          AND restaurant_id = :restaurantId
          AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
          )
        ORDER BY created_at DESC
        LIMIT 1
    """)
    suspend fun getLatestPendingOnlineBill(restaurantId: Long): BillEntity?

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
        AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
        )
    """)
    suspend fun cancelStalePendingOnlineDrafts(reason: String, updatedAt: Long, restaurantId: Long): Int

    @Query(
            "SELECT * FROM bills WHERE created_at BETWEEN :startMillis AND :endMillis AND is_deleted = 0 AND restaurant_id = :restaurantId ORDER BY created_at DESC"
    )
    fun getBillsByDateRange(startMillis: Long, endMillis: Long, restaurantId: Long): Flow<List<BillEntity>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id AND restaurant_id = :restaurantId")
    suspend fun getBillWithItemsById(id: Long, restaurantId: Long): BillWithItems?

    @Transaction
    @Query("SELECT * FROM bills WHERE lifetime_order_id = :id AND restaurant_id = :restaurantId")
    suspend fun getBillWithItemsByLifetimeId(id: Long, restaurantId: Long): BillWithItems?

    @Transaction
    suspend fun insertFullBill(
            bill: BillEntity,
            items: List<BillItemEntity>,
            payments: List<BillPaymentEntity>
    ) {
        val billId = insertBill(bill)
        val itemsWithId = items.map {
            it.copy(
                billId = billId,
                restaurantId = bill.restaurantId,
                deviceId = bill.deviceId
            )
        }
        val paymentsWithId = payments.map { it.copy(billId = billId, restaurantId = bill.restaurantId, deviceId = bill.deviceId) }
        insertBillItems(itemsWithId)
        insertBillPayments(paymentsWithId)
    }

    // ── Sync reconciliation ────────────────────────────────────────────────────

    /**
     * Marks any bill that has a server_id assigned (from a successful push
     * round-trip or from a server pull) as synced. Catches bills where the push
     * network response was lost but the server actually saved the record.
     */
    @Query("UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL WHERE is_synced = 0 AND server_id IS NOT NULL AND restaurant_id = :restaurantId")
    suspend fun reconcileServerAcknowledgedBills(restaurantId: Long): Int

    /**
     * After a pull, the server returns bills with their server-assigned IDs.
     * For bills created on THIS device, the original local row (with device-local pk)
     * may still be isSynced=0 because the pull upserted a NEW row keyed by the
     * server ID. This query finds those orphaned local rows by matching on
     * (device_id, lifetime_order_id) — which is unique per device — and marks them synced.
     */
    @Query("""
        UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL
        WHERE is_synced = 0
          AND device_id = :deviceId
          AND restaurant_id = :restaurantId
          AND lifetime_order_id IN (:lifetimeOrderIds)
    """)
    suspend fun markBillsSyncedByLifetimeIds(deviceId: String, restaurantId: Long, lifetimeOrderIds: List<Long>): Int

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
     */
    @Query("""
        UPDATE bills SET is_synced = 1, sync_status = 'synced', sync_failure_reason = NULL, sync_failed_at = NULL
        WHERE is_synced = 0
          AND device_id = :deviceId
          AND restaurant_id = :restaurantId
          AND id IN (:localIds)
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBills(bills: List<BillEntity>)

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

    @Query("UPDATE bill_items SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markBillItemsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE bill_items SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateBillItemServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBillItems(items: List<BillItemEntity>)

    @Query("DELETE FROM bill_items WHERE is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteAllSyncedBillItems(restaurantId: Long)

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

    @Query("UPDATE bill_payments SET is_synced = 1 WHERE id IN (:ids) AND restaurant_id = :restaurantId")
    suspend fun markBillPaymentsAsSynced(ids: List<Long>, restaurantId: Long)

    @Query("UPDATE bill_payments SET server_id = :serverId WHERE id = :localId AND restaurant_id = :restaurantId")
    suspend fun updateBillPaymentServerIdByLocalId(localId: Long, serverId: Long, restaurantId: Long)

    @Query("""
        SELECT item_name as itemName, SUM(quantity) as quantitySold, SUM(item_total) as revenue
        FROM bill_items
        INNER JOIN bills ON bill_items.bill_id = bills.id
        WHERE bills.order_status = 'completed' AND bills.is_deleted = 0 AND bills.restaurant_id = :restaurantId AND bills.created_at BETWEEN :startMillis AND :endMillis
        GROUP BY item_name
        ORDER BY quantitySold DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int, restaurantId: Long): List<com.khanabook.lite.pos.domain.model.TopSellingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBillPayments(payments: List<BillPaymentEntity>)

    @Query("DELETE FROM bill_payments WHERE is_synced = 1 AND restaurant_id = :restaurantId")
    suspend fun deleteAllSyncedBillPayments(restaurantId: Long)

    @Query("SELECT * FROM bills WHERE customer_whatsapp IS NOT NULL AND customer_whatsapp != '' AND order_status != 'cancelled' AND restaurant_id = :restaurantId ORDER BY created_at DESC LIMIT 20")
    suspend fun getRecentBillsWithCustomers(restaurantId: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE lifetime_order_id = :lifetimeNo AND restaurant_id = :restaurantId AND is_deleted = 0 LIMIT 1")
    suspend fun getBillByLifetimeNo(lifetimeNo: Long, restaurantId: Long): BillEntity?

    @Query(
        """
        SELECT b.* FROM bills b
        INNER JOIN kitchen_print_queue kpq ON b.id = kpq.bill_id
        WHERE kpq.dispatch_status != 'SENT'
          AND b.restaurant_id = :restaurantId
        ORDER BY b.created_at DESC
        """
    )
    suspend fun getBillsWithPendingKds(restaurantId: Long): List<BillEntity>
}
