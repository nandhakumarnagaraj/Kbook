package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Query("SELECT id, server_id as serverId FROM bills WHERE server_id IS NOT NULL")
    suspend fun getAllBillServerIds(): List<com.khanabook.lite.pos.domain.model.ServerIdMapping>

    @Query("SELECT * FROM bills WHERE server_id = :serverId LIMIT 1")
    suspend fun getBillByServerId(serverId: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE id = :localId AND device_id = :deviceId LIMIT 1")
    suspend fun getBillByLocalId(localId: Long, deviceId: String): BillEntity?

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

    @Query("SELECT * FROM bills WHERE id = :id") suspend fun getBillById(id: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE lifetime_order_id = :id")
    suspend fun getBillByLifetimeId(id: Long): BillEntity?

    @Query(
            "SELECT * FROM bills WHERE daily_order_display = :displayId AND created_at BETWEEN :startTime AND :endTime"
    )
    suspend fun getBillByDailyIdAndDate(displayId: String, startTime: Long, endTime: Long): BillEntity?

    @Query(
            "SELECT * FROM bills WHERE daily_order_id = :dailyId AND created_at BETWEEN :startTime AND :endTime"
    )
    suspend fun getBillByDailyIntIdAndDate(dailyId: Long, startTime: Long, endTime: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE order_status = 'draft'")
    fun getDraftBills(): Flow<List<BillEntity>>

    @Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND payment_status = 'pending'
          AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
          )
        ORDER BY created_at DESC
        LIMIT 1
    """)
    suspend fun getLatestPendingOnlineBill(): BillEntity?

    @Query("UPDATE bills SET order_status = :status WHERE id = :id")
    suspend fun updateOrderStatus(id: Long, status: String)

    @Query("UPDATE bills SET payment_mode = :mode WHERE id = :id")
    suspend fun updatePaymentMode(id: Long, mode: String)

    @Query("UPDATE bills SET payment_status = :status WHERE id = :id")
    suspend fun updatePaymentStatus(id: Long, status: String)

    @Query("UPDATE bills SET order_status = 'cancelled', cancel_reason = :reason, is_synced = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun cancelBill(id: Long, reason: String, updatedAt: Long)

    /**
     * All Easebuzz (online) draft bills older than [olderThanMs] ms — for background payment recovery.
     *
     * Easebuzz orders are stored with payment_mode = 'online' (PaymentMode.ONLINE.dbValue) and
     * payment_status = 'pending' until the gateway confirms. gateway_txn_id is NOT set at draft
     * creation (only after the payment result arrives), so we must not filter on it here — these
     * are exactly the started-but-unconfirmed orders the worker needs to reconcile.
     */
    @Query("""
        SELECT * FROM bills
        WHERE order_status = 'draft'
          AND payment_mode = 'online'
          AND payment_status = 'pending'
          AND created_at < :olderThanMs
          AND is_deleted = 0
        ORDER BY created_at ASC
    """)
    suspend fun getPendingEasebuzzDrafts(olderThanMs: Long): List<BillEntity>

    /** Mark a bill as completed after gateway payment confirmed. */
    @Query("""
        UPDATE bills SET
            order_status = 'completed',
            payment_status = 'paid',
            is_synced = 0,
            updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun markBillCompleted(id: Long, updatedAt: Long)

    @Query("""
        UPDATE bills SET order_status = 'cancelled', payment_status = 'failed',
        cancel_reason = :reason, is_synced = 0, updated_at = :updatedAt
        WHERE order_status = 'draft' AND payment_status = 'pending'
        AND is_synced = 0
        AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
        )
    """)
    suspend fun cancelStalePendingOnlineDrafts(reason: String, updatedAt: Long): Int

    /** Cancel ALL pending online drafts (including synced ones) for explicit user-initiated cancel. */
    @Query("""
        UPDATE bills SET order_status = 'cancelled', payment_status = 'failed',
        cancel_reason = :reason, is_synced = 0, updated_at = :updatedAt
        WHERE order_status = 'draft' AND payment_status = 'pending'
        AND payment_mode IN (
            'upi', 'part_cash_upi', 'part_upi_pos'
        )
    """)
    suspend fun cancelAllPendingOnlineDrafts(reason: String, updatedAt: Long): Int

    @Query(
            "SELECT * FROM bills WHERE created_at BETWEEN :startMillis AND :endMillis AND is_deleted = 0 ORDER BY created_at DESC"
    )
    fun getBillsByDateRange(startMillis: Long, endMillis: Long): Flow<List<BillEntity>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBillWithItemsById(id: Long): BillWithItems?

    @Transaction
    @Query("SELECT * FROM bills WHERE lifetime_order_id = :id")
    suspend fun getBillWithItemsByLifetimeId(id: Long): BillWithItems?

    @Transaction
    suspend fun insertFullBill(
            bill: BillEntity,
            items: List<BillItemEntity>,
            payments: List<BillPaymentEntity>
    ): Long {
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
        return billId
    }

    

    @Query("UPDATE bills SET is_synced = 1 WHERE is_synced = 0 AND server_id IS NOT NULL")
    suspend fun reconcileServerAcknowledgedBills(): Int

    @Query("SELECT * FROM bills WHERE is_synced = 0")
    suspend fun getUnsyncedBills(): List<BillEntity>

    @Query("UPDATE bills SET is_synced = 1 WHERE id IN (:billIds)")
    suspend fun markBillsAsSynced(billIds: List<Long>)

    @Query("UPDATE bills SET server_id = :serverId WHERE id = :localId")
    suspend fun updateServerIdByLocalId(localId: Long, serverId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBills(bills: List<BillEntity>)

    @Query("SELECT COUNT(*) FROM bills WHERE is_synced = 0")
    suspend fun getUnsyncedCountOnce(): Int

    @Query("SELECT COUNT(*) FROM bills WHERE is_synced = 0")
    fun getUnsyncedCount(): Flow<Int>

    
    @Query("SELECT * FROM bill_items WHERE is_synced = 0")
    suspend fun getUnsyncedBillItems(): List<BillItemEntity>

    @Query("UPDATE bill_items SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markBillItemsAsSynced(ids: List<Long>)

    @Query("UPDATE bill_items SET server_bill_id = :serverBillId WHERE bill_id = :localBillId AND server_bill_id IS NULL")
    suspend fun updateBillItemsServerBillId(localBillId: Long, serverBillId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBillItems(items: List<BillItemEntity>)

    @Query("DELETE FROM bill_items WHERE is_synced = 1")
    suspend fun deleteAllSyncedBillItems()

    
    @Query("SELECT * FROM bill_payments WHERE is_synced = 0")
    suspend fun getUnsyncedBillPayments(): List<BillPaymentEntity>

    @Query("UPDATE bill_payments SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markBillPaymentsAsSynced(ids: List<Long>)

    @Query("""
        SELECT item_name as itemName, SUM(quantity) as quantitySold, SUM(item_total) as revenue
        FROM bill_items
        INNER JOIN bills ON bill_items.bill_id = bills.id
        WHERE bills.order_status = 'completed' AND bills.is_deleted = 0 AND bills.created_at BETWEEN :startMillis AND :endMillis
        GROUP BY item_name
        ORDER BY quantitySold DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int): List<com.khanabook.lite.pos.domain.model.TopSellingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBillPayments(payments: List<BillPaymentEntity>)

    @Query("DELETE FROM bill_payments WHERE is_synced = 1")
    suspend fun deleteAllSyncedBillPayments()

    @Query("SELECT * FROM bills WHERE customer_whatsapp IS NOT NULL AND customer_whatsapp != '' ORDER BY created_at DESC LIMIT 20")
    suspend fun getRecentBillsWithCustomers(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE lifetime_order_id = :lifetimeNo AND is_deleted = 0 LIMIT 1")
    suspend fun getBillByLifetimeNo(lifetimeNo: Long): BillEntity?

    @Query(
        """
        SELECT b.* FROM bills b
        INNER JOIN kitchen_print_queue kpq ON b.id = kpq.bill_id
        WHERE kpq.dispatch_status != 'SENT'
        ORDER BY b.created_at DESC
        """
    )
    suspend fun getBillsWithPendingKds(): List<BillEntity>
}
