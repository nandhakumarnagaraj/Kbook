package com.khanabook.lite.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khanabook.lite.pos.data.local.entity.KitchenPrintQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KitchenPrintQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: KitchenPrintQueueEntity): Long

    @Query("SELECT * FROM kitchen_print_queue WHERE printer_mac = :printerMac OR printer_mac = '' ORDER BY created_at ASC")
    suspend fun getPendingForPrinter(printerMac: String): List<KitchenPrintQueueEntity>

    @Query("SELECT * FROM kitchen_print_queue WHERE bill_id = :billId AND printer_mac = :printerMac LIMIT 1")
    suspend fun getByBillAndPrinter(billId: Long, printerMac: String): KitchenPrintQueueEntity?

    @Query("SELECT COUNT(*) FROM kitchen_print_queue")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM kitchen_print_queue WHERE bill_id = :billId")
    suspend fun getPendingCountForBill(billId: Long): Int

    @Query("DELETE FROM kitchen_print_queue WHERE bill_id = :billId AND printer_mac = :printerMac")
    suspend fun deleteByBillAndPrinter(billId: Long, printerMac: String)

    @Query("DELETE FROM kitchen_print_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM kitchen_print_queue WHERE bill_id = :billId")
    suspend fun deleteByBillId(billId: Long)
}
