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

    @Query("UPDATE bills SET order_status = :status WHERE id = :id")
    suspend fun updateOrderStatus(id: Long, status: String)

    @Query("UPDATE bills SET payment_mode = :mode WHERE id = :id")
    suspend fun updatePaymentMode(id: Long, mode: String)

    @Query("UPDATE bills SET payment_status = :status WHERE id = :id")
    suspend fun updatePaymentStatus(id: Long, status: String)

    @Query("UPDATE bills SET order_status = 'cancelled', cancel_reason = :reason, is_synced = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun cancelBill(id: Long, reason: String, updatedAt: Long)

    @Query(
            "SELECT * FROM bills WHERE created_at BETWEEN :startMillis AND :endMillis ORDER BY created_at DESC"
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

    

    @Query("SELECT * FROM bills WHERE is_synced = 0")
    suspend fun getUnsyncedBills(): List<BillEntity>

    @Query("UPDATE bills SET is_synced = 1 WHERE id IN (:billIds)")
    suspend fun markBillsAsSynced(billIds: List<Long>)

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
        WHERE bills.order_status = 'completed' AND bills.created_at BETWEEN :startMillis AND :endMillis
        GROUP BY item_name
        ORDER BY quantitySold DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int): List<com.khanabook.lite.pos.domain.model.TopSellingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedBillPayments(payments: List<BillPaymentEntity>)

    @Query("DELETE FROM bill_payments WHERE is_synced = 1")
    suspend fun deleteAllSyncedBillPayments()
}
