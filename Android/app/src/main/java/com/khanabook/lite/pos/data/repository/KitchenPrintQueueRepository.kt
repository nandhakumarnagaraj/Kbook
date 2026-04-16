package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.local.dao.KitchenPrintQueueDao
import com.khanabook.lite.pos.data.local.entity.KitchenPrintQueueEntity
import kotlinx.coroutines.flow.Flow

class KitchenPrintQueueRepository(
    private val kitchenPrintQueueDao: KitchenPrintQueueDao
) {
    companion object {
        const val UNASSIGNED_PRINTER_MAC = ""
    }

    fun getPendingCountFlow(): Flow<Int> = kitchenPrintQueueDao.getPendingCountFlow()

    suspend fun hasPendingForBill(billId: Long): Boolean =
        kitchenPrintQueueDao.getPendingCountForBill(billId) > 0

    suspend fun enqueueOrUpdate(billId: Long, printerMac: String, error: String?) {
        val now = System.currentTimeMillis()
        val existing = kitchenPrintQueueDao.getByBillAndPrinter(billId, printerMac)
        kitchenPrintQueueDao.upsert(
            KitchenPrintQueueEntity(
                id = existing?.id ?: 0,
                billId = billId,
                printerMac = printerMac,
                attempts = (existing?.attempts ?: 0) + 1,
                lastError = error,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun getPendingForPrinter(printerMac: String): List<KitchenPrintQueueEntity> =
        kitchenPrintQueueDao.getPendingForPrinter(printerMac)

    suspend fun getByBillAndPrinter(billId: Long, printerMac: String): KitchenPrintQueueEntity? =
        kitchenPrintQueueDao.getByBillAndPrinter(billId, printerMac)

    suspend fun deleteByBillAndPrinter(billId: Long, printerMac: String) {
        kitchenPrintQueueDao.deleteByBillAndPrinter(billId, printerMac)
    }

    suspend fun deleteById(id: Long) {
        kitchenPrintQueueDao.deleteById(id)
    }

    suspend fun deleteByBillId(billId: Long) {
        kitchenPrintQueueDao.deleteByBillId(billId)
    }
}
