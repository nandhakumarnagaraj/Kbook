package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.local.dao.KitchenPrintQueueDao
import com.khanabook.lite.pos.data.local.entity.KitchenPrintDispatchStatus
import com.khanabook.lite.pos.data.local.entity.KitchenPrintQueueEntity
import com.khanabook.lite.pos.domain.manager.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class KitchenPrintQueueRepository(
    private val kitchenPrintQueueDao: KitchenPrintQueueDao,
    private val sessionManager: SessionManager
) {
    companion object {
        const val UNASSIGNED_PRINTER_MAC = ""
    }

    fun getPendingCountFlow(): Flow<Int> =
        sessionManager.restaurantId.flatMapLatest { restaurantId ->
            kitchenPrintQueueDao.getPendingCountFlow(restaurantId)
        }

    suspend fun hasPendingForBill(billId: Long): Boolean =
        kitchenPrintQueueDao.getPendingCountForBill(billId) > 0

    suspend fun enqueuePending(
        billId: Long,
        printerMac: String,
        error: String?,
        incrementAttempts: Boolean = false,
        publicToken: String? = null,
        kotRevision: String? = null
    ) {
        val now = System.currentTimeMillis()
        val existing = kitchenPrintQueueDao.getByBillAndPrinter(billId, printerMac)
        kitchenPrintQueueDao.upsert(
            KitchenPrintQueueEntity(
                id = existing?.id ?: 0,
                billId = billId,
                restaurantId = existing?.restaurantId ?: sessionManager.getRestaurantId(),
                printerMac = printerMac,
                attempts = when {
                    incrementAttempts && existing != null -> existing.attempts + 1
                    incrementAttempts -> 1
                    else -> existing?.attempts ?: 0
                },
                lastError = error,
                dispatchStatus = KitchenPrintDispatchStatus.PENDING,
                lastAttemptAt = when {
                    incrementAttempts -> now
                    else -> existing?.lastAttemptAt
                },
                publicToken = publicToken ?: existing?.publicToken,
                kotRevision = kotRevision ?: existing?.kotRevision,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun getPendingForPrinter(printerMac: String): List<KitchenPrintQueueEntity> =
        kitchenPrintQueueDao.getPendingForPrinter(printerMac, sessionManager.getRestaurantId())

    suspend fun getByBillAndPrinter(billId: Long, printerMac: String): KitchenPrintQueueEntity? =
        kitchenPrintQueueDao.getByBillAndPrinter(billId, printerMac)

    suspend fun getById(id: Long): KitchenPrintQueueEntity? = kitchenPrintQueueDao.getById(id)

    suspend fun claimPendingForRetry(id: Long): Boolean =
        kitchenPrintQueueDao.markRetryingIfPending(id, System.currentTimeMillis()) > 0

    suspend fun claimPendingForDirectPrint(billId: Long, printerMac: String): Boolean =
        kitchenPrintQueueDao.markRetryingIfPending(billId, printerMac, System.currentTimeMillis()) > 0

    suspend fun markPending(id: Long, error: String?) {
        kitchenPrintQueueDao.markPending(id, error, System.currentTimeMillis())
    }

    suspend fun markSent(id: Long) {
        kitchenPrintQueueDao.markSent(id, System.currentTimeMillis())
    }

    suspend fun markSentIfPresent(billId: Long, printerMac: String) {
        kitchenPrintQueueDao.markSent(billId, printerMac, System.currentTimeMillis())
    }

    suspend fun deleteById(id: Long) {
        kitchenPrintQueueDao.deleteById(id)
    }

    suspend fun deleteByBillId(billId: Long) {
        kitchenPrintQueueDao.deleteByBillId(billId)
    }
}
