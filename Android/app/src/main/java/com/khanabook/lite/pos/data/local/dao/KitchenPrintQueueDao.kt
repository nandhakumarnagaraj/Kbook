package com.khanabook.lite.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khanabook.lite.pos.data.local.entity.KitchenPrintQueueEntity
import com.khanabook.lite.pos.data.local.entity.KitchenPrintDispatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface KitchenPrintQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: KitchenPrintQueueEntity): Long

    @Query(
        """
        SELECT * FROM kitchen_print_queue
        WHERE (printer_mac = :printerMac OR printer_mac = '')
          AND dispatch_status = '${KitchenPrintDispatchStatus.PENDING}'
        ORDER BY created_at ASC
        """
    )
    suspend fun getPendingForPrinter(printerMac: String): List<KitchenPrintQueueEntity>

    @Query("SELECT * FROM kitchen_print_queue WHERE bill_id = :billId AND printer_mac = :printerMac LIMIT 1")
    suspend fun getByBillAndPrinter(billId: Long, printerMac: String): KitchenPrintQueueEntity?

    @Query("SELECT * FROM kitchen_print_queue WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KitchenPrintQueueEntity?

    @Query(
        """
        SELECT COUNT(*) FROM kitchen_print_queue
        WHERE dispatch_status != '${KitchenPrintDispatchStatus.SENT}'
        """
    )
    fun getPendingCountFlow(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM kitchen_print_queue
        WHERE bill_id = :billId
          AND dispatch_status != '${KitchenPrintDispatchStatus.SENT}'
        """
    )
    suspend fun getPendingCountForBill(billId: Long): Int

    @Query(
        """
        UPDATE kitchen_print_queue
        SET dispatch_status = '${KitchenPrintDispatchStatus.RETRYING}',
            attempts = attempts + 1,
            last_attempt_at = :attemptedAt,
            updated_at = :attemptedAt
        WHERE id = :id
          AND dispatch_status = '${KitchenPrintDispatchStatus.PENDING}'
        """
    )
    suspend fun markRetryingIfPending(id: Long, attemptedAt: Long): Int

    @Query(
        """
        UPDATE kitchen_print_queue
        SET dispatch_status = '${KitchenPrintDispatchStatus.RETRYING}',
            attempts = attempts + 1,
            last_attempt_at = :attemptedAt,
            updated_at = :attemptedAt
        WHERE bill_id = :billId
          AND printer_mac = :printerMac
          AND dispatch_status = '${KitchenPrintDispatchStatus.PENDING}'
        """
    )
    suspend fun markRetryingIfPending(
        billId: Long,
        printerMac: String,
        attemptedAt: Long
    ): Int

    @Query(
        """
        UPDATE kitchen_print_queue
        SET dispatch_status = '${KitchenPrintDispatchStatus.PENDING}',
            last_error = :error,
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markPending(id: Long, error: String?, updatedAt: Long)

    @Query(
        """
        UPDATE kitchen_print_queue
        SET dispatch_status = '${KitchenPrintDispatchStatus.SENT}',
            last_error = NULL,
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markSent(id: Long, updatedAt: Long)

    @Query(
        """
        UPDATE kitchen_print_queue
        SET dispatch_status = '${KitchenPrintDispatchStatus.SENT}',
            last_error = NULL,
            updated_at = :updatedAt
        WHERE bill_id = :billId
          AND printer_mac = :printerMac
        """
    )
    suspend fun markSent(billId: Long, printerMac: String, updatedAt: Long)

    @Query("DELETE FROM kitchen_print_queue WHERE bill_id = :billId AND printer_mac = :printerMac")
    suspend fun deleteByBillAndPrinter(billId: Long, printerMac: String)

    @Query("DELETE FROM kitchen_print_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM kitchen_print_queue WHERE bill_id = :billId")
    suspend fun deleteByBillId(billId: Long)
}
