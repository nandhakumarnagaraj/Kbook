package com.khanabook.lite.pos.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager
import com.khanabook.lite.pos.worker.MasterSyncWorker
import kotlinx.coroutines.flow.Flow

class BillRepository(
        private val billDao: BillDao,
        private val restaurantDao: com.khanabook.lite.pos.data.local.dao.RestaurantDao,
        private val inventoryConsumptionManager: InventoryConsumptionManager? = null,
        private val workManager: WorkManager,
        private val kitchenPrintQueueRepository: KitchenPrintQueueRepository? = null
) {

    suspend fun insertFullBill(
            bill: BillEntity,
            items: List<BillItemEntity>,
            payments: List<BillPaymentEntity>
    ) {
        billDao.insertFullBill(bill, items, payments)
        
        if (bill.orderStatus.equals("completed", ignoreCase = true) ||
            bill.orderStatus.equals("paid", ignoreCase = true)
        ) {
            inventoryConsumptionManager?.consumeMaterialsForBill(items)
        }
        
        triggerBackgroundSync()
    }

    private fun triggerBackgroundSync() {
        val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncWorkRequest =
                OneTimeWorkRequestBuilder<MasterSyncWorker>().setConstraints(constraints).build()
        workManager.enqueueUniqueWork(
            "MasterSyncWorker_OneTime",
            ExistingWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }

    suspend fun getBillById(id: Long): BillEntity? {
        return billDao.getBillById(id)
    }

    suspend fun getBillWithItemsById(id: Long): BillWithItems? {
        return billDao.getBillWithItemsById(id)
    }

    suspend fun getBillWithItemsByLifetimeId(id: Long): BillWithItems? {
        return billDao.getBillWithItemsByLifetimeId(id)
    }

    suspend fun updateBill(bill: BillEntity) {
        billDao.updateBill(bill)
        triggerBackgroundSync()
    }

    suspend fun addBillPayment(payment: BillPaymentEntity) {
        billDao.insertBillPayment(payment)
        triggerBackgroundSync()
    }

    suspend fun getBillByDailyIdAndDate(displayId: String, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(date)
        return billDao.getBillByDailyIdAndDate(displayId, start, end)
    }

    suspend fun getBillByDailyIntIdAndDate(dailyId: Long, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(date)
        return billDao.getBillByDailyIntIdAndDate(dailyId, start, end)
    }

    fun getDraftBills(): Flow<List<BillEntity>> {
        return billDao.getDraftBills()
    }

    suspend fun getLatestPendingOnlineBill(): BillEntity? {
        return billDao.getLatestPendingOnlineBill()
    }

    suspend fun updateOrderStatus(id: Long, status: String) {
        val current = billDao.getBillById(id) ?: return
        
        
        val wasDeducted = current.orderStatus.equals("completed", ignoreCase = true) || 
                          current.orderStatus.equals("paid", ignoreCase = true)
        val isBecomingDeducted = status.equals("completed", ignoreCase = true) || 
                                 status.equals("paid", ignoreCase = true)

        billDao.updateBill(
            current.copy(
                orderStatus = status,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )

        
        if (isBecomingDeducted && !wasDeducted) {
            val billWithItems = billDao.getBillWithItemsById(id)
            billWithItems?.let { inventoryConsumptionManager?.consumeMaterialsForBill(it.items) }
        }
        triggerBackgroundSync()
    }

    suspend fun cancelOrder(id: Long, reason: String) {
        billDao.cancelBill(id, reason, System.currentTimeMillis())
        kitchenPrintQueueRepository?.deleteByBillId(id)
        triggerBackgroundSync()
    }

    suspend fun cancelStalePendingOnlineDrafts(): Int {
        val cancelled = billDao.cancelStalePendingOnlineDrafts(
            reason = "Superseded by new payment attempt",
            updatedAt = System.currentTimeMillis()
        )
        if (cancelled > 0) triggerBackgroundSync()
        return cancelled
    }

    suspend fun updatePaymentMode(id: Long, mode: String, partAmount1: String = "0.0", partAmount2: String = "0.0") {
        val current = billDao.getBillById(id) ?: return
        if (current.orderStatus.equals("cancelled", ignoreCase = true)) return
        billDao.updateBill(
            current.copy(
                paymentMode = mode,
                partAmount1 = partAmount1,
                partAmount2 = partAmount2,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        triggerBackgroundSync()
    }

    suspend fun updatePaymentStatus(id: Long, status: String) {
        val current = billDao.getBillById(id) ?: return
        billDao.updateBill(
            current.copy(
                paymentStatus = status,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        triggerBackgroundSync()
    }

    fun getBillsByDateRange(startDate: String, endDate: String): Flow<List<BillEntity>> {
        
        val startMillis = try { 
            if (startDate.contains(":")) {
               java.time.LocalDateTime.parse(startDate.replace(" ", "T")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
               com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(startDate)
            }
        } catch (e: Exception) { 0L }
        
        val endMillis = try {
            if (endDate.contains(":")) {
               java.time.LocalDateTime.parse(endDate.replace(" ", "T")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
               com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(endDate)
            }
        } catch (e: Exception) { Long.MAX_VALUE }
        
        return getBillsByDateRange(startMillis, endMillis)
    }

    fun getBillsByDateRange(startMillis: Long, endMillis: Long): Flow<List<BillEntity>> {
        return billDao.getBillsByDateRange(startMillis, endMillis)
    }

    fun getProfileFlow(): Flow<com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?> {
        return restaurantDao.getProfileFlow()
    }

    fun getUnsyncedCount(): Flow<Int> {
        return billDao.getUnsyncedCount()
    }

    suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int): List<com.khanabook.lite.pos.domain.model.TopSellingItem> {
        return billDao.getTopSellingItemsInRange(startMillis, endMillis, limit)
    }

    suspend fun getRecentCustomers(limit: Int = 5): List<Pair<String, String>> {
        return billDao.getRecentBillsWithCustomers()
            .distinctBy { it.customerWhatsapp }
            .take(limit)
            .mapNotNull { bill ->
                val phone = bill.customerWhatsapp ?: return@mapNotNull null
                phone to (bill.customerName ?: "")
            }
    }

    suspend fun getBillWithItemsByInvoiceNo(invoiceNo: Long): BillWithItems? {
        return billDao.getBillByLifetimeNo(invoiceNo)?.let { bill ->
            billDao.getBillWithItemsById(bill.id)
        }
    }

    suspend fun getBillsWithPendingKds(): List<BillWithItems> {
        return billDao.getBillsWithPendingKds().mapNotNull { bill ->
            billDao.getBillWithItemsById(bill.id)
        }
    }
}
