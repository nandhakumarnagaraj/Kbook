package com.khanabook.lite.pos.data.repository

import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager
import com.khanabook.lite.pos.domain.util.enqueueMasterSyncOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class BillRepository(
        private val billDao: BillDao,
        private val restaurantDao: com.khanabook.lite.pos.data.local.dao.RestaurantDao,
        private val inventoryConsumptionManager: InventoryConsumptionManager? = null,
        private val workManager: WorkManager,
        private val kitchenPrintQueueRepository: KitchenPrintQueueRepository? = null,
        private val sessionManager: SessionManager
) {

    suspend fun insertFullBill(
            bill: BillEntity,
            items: List<BillItemEntity>,
            payments: List<BillPaymentEntity>
    ): Long {
        val billId = billDao.insertFullBill(bill, items, payments)
        
        if (bill.orderStatus.equals("completed", ignoreCase = true) ||
            bill.orderStatus.equals("paid", ignoreCase = true)
        ) {
            inventoryConsumptionManager?.consumeMaterialsForBill(items)
        }
        
        triggerBackgroundSync()
        return billId
    }

    private fun triggerBackgroundSync() {
        workManager.enqueueMasterSyncOnce()
    }

    suspend fun getBillById(id: Long): BillEntity? {
        return billDao.getBillById(id, sessionManager.getRestaurantId())
    }

    suspend fun getBillWithItemsById(id: Long): BillWithItems? {
        return billDao.getBillWithItemsById(id, sessionManager.getRestaurantId())
    }

    suspend fun updateBill(bill: BillEntity) {
        billDao.updateBill(bill)
        triggerBackgroundSync()
    }

    suspend fun addBillPayment(payment: BillPaymentEntity) {
        billDao.insertBillPayment(payment)
        triggerBackgroundSync()
    }

    suspend fun addBillPayments(payments: List<BillPaymentEntity>) {
        billDao.insertBillPayments(payments)
        triggerBackgroundSync()
    }

    suspend fun settleDraftBill(
        bill: BillEntity,
        payments: List<BillPaymentEntity>
    ) {
        billDao.settleDraftBill(bill, payments)
        triggerBackgroundSync()
    }

    suspend fun getBillByDailyIdAndDate(displayId: String, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(date)
        return billDao.getBillByDailyIdAndDate(displayId, start, end, sessionManager.getRestaurantId())
    }

    suspend fun getBillByDailyIntIdAndDate(dailyId: Long, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(date)
        return billDao.getBillByDailyIntIdAndDate(dailyId, start, end, sessionManager.getRestaurantId())
    }

    fun getDraftBills(): Flow<List<BillEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getDraftBills(restaurantId)
        }
    }

    suspend fun getLatestPendingOnlineBill(): BillEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        val activeUserId = sessionManager.getActiveUserId()
        return if (activeUserId != null) {
            billDao.getLatestPendingOnlineBill(restaurantId, activeUserId)
                ?: billDao.getLatestPendingOnlineBill(restaurantId)
        } else {
            billDao.getLatestPendingOnlineBill(restaurantId)
        }
    }

    fun getPendingOnlineBillsFlow(): Flow<List<BillEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getPendingOnlineBillsFlow(restaurantId)
        }
    }

    fun getSyncQuarantineCountFlow(): Flow<Int> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getSyncQuarantineCountFlow(restaurantId)
        }
    }

    suspend fun updateOrderStatus(id: Long, status: String) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = billDao.getBillById(id, restaurantId) ?: return
        
        
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
            val billWithItems = billDao.getBillWithItemsById(id, restaurantId)
            billWithItems?.let { inventoryConsumptionManager?.consumeMaterialsForBill(it.items) }
        }
        triggerBackgroundSync()
    }

    suspend fun cancelOrder(id: Long, reason: String) {
        billDao.cancelBill(id, reason, System.currentTimeMillis(), sessionManager.getRestaurantId())
        kitchenPrintQueueRepository?.deleteByBillId(id)
        triggerBackgroundSync()
    }

    suspend fun cancelStalePendingOnlineDrafts(): Int {
        val cancelled = billDao.cancelStalePendingOnlineDrafts(
            reason = "Superseded by new payment attempt",
            updatedAt = System.currentTimeMillis(),
            restaurantId = sessionManager.getRestaurantId()
        )
        if (cancelled > 0) triggerBackgroundSync()
        return cancelled
    }

    suspend fun updatePaymentMode(id: Long, mode: String, partAmount1: String = "0.0", partAmount2: String = "0.0") {
        val current = billDao.getBillById(id, sessionManager.getRestaurantId()) ?: return
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
        val current = billDao.getBillById(id, sessionManager.getRestaurantId()) ?: return
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
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getBillsByDateRange(startMillis, endMillis, restaurantId)
        }
    }

    fun getProfileFlow(): Flow<com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity?> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            if (restaurantId > 0) restaurantDao.getProfileFlow(restaurantId) else restaurantDao.getProfileFlow()
        }
    }

    fun getUnsyncedCount(): Flow<Int> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getUnsyncedCount(restaurantId)
        }
    }

    suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int): List<com.khanabook.lite.pos.domain.model.TopSellingItem> {
        return billDao.getTopSellingItemsInRange(startMillis, endMillis, limit, sessionManager.getRestaurantId())
    }

    suspend fun getRecentCustomers(limit: Int = 5): List<Pair<String, String>> {
        return billDao.getRecentBillsWithCustomers(sessionManager.getRestaurantId())
            .distinctBy { it.customerWhatsapp }
            .take(limit)
            .mapNotNull { bill ->
                val phone = bill.customerWhatsapp ?: return@mapNotNull null
                phone to (bill.customerName ?: "")
            }
    }

    suspend fun getRecentDineInCustomers(limit: Int = 5): List<Pair<String, String>> {
        return billDao.getRecentDineInBillsWithCustomers(sessionManager.getRestaurantId())
            .distinctBy { it.customerName }
            .take(limit)
            .mapNotNull { bill ->
                val name = bill.customerName ?: return@mapNotNull null
                (bill.customerWhatsapp ?: "") to name
            }
    }

    // New GST invoice-number lookup (e.g. "26A1-000042").
    suspend fun getBillWithItemsByInvoiceNumber(invoiceNumber: String): BillWithItems? {
        return billDao.getBillWithItemsByInvoiceNumber(invoiceNumber, sessionManager.getRestaurantId())
    }

    suspend fun getMaxInvoiceSequence(terminalSeries: String, financialYear: String): Long {
        return billDao.getMaxInvoiceSequence(
            restaurantId = sessionManager.getRestaurantId(),
            terminalSeries = terminalSeries,
            financialYear = financialYear
        )
    }

    // Legacy fallback: bills issued before the new numbering used lifetime_order_id (e.g. "INV42").
    suspend fun getBillWithItemsByLegacyInvoiceNo(invoiceNo: Long): BillWithItems? {
        return billDao.getBillByLifetimeNo(invoiceNo, sessionManager.getRestaurantId())?.let { bill ->
            billDao.getBillWithItemsById(bill.id, sessionManager.getRestaurantId())
        }
    }

    suspend fun getBillsWithPendingKds(): List<BillWithItems> {
        return billDao.getBillsWithPendingKds(sessionManager.getRestaurantId()).mapNotNull { bill ->
            billDao.getBillWithItemsById(bill.id, sessionManager.getRestaurantId())
        }
    }

    suspend fun getUnsentItemsForBill(billId: Long): List<BillItemEntity> {
        return billDao.getUnsentItemsForBill(billId, sessionManager.getRestaurantId())
    }

    suspend fun markItemsSentToKot(itemIds: List<Long>) {
        billDao.markItemsSentToKot(itemIds, sessionManager.getRestaurantId())
    }

    suspend fun insertBillItems(items: List<BillItemEntity>) {
        billDao.insertBillItems(items)
        triggerBackgroundSync()
    }

    suspend fun updateBillItem(item: BillItemEntity) {
        billDao.updateBillItem(item)
        triggerBackgroundSync()
    }

    suspend fun deleteBillItemById(id: Long) {
        billDao.deleteBillItemById(id)
        triggerBackgroundSync()
    }

    fun getActiveDraftBillsFlow(): kotlinx.coroutines.flow.Flow<List<BillEntity>> {
        return billDao.getActiveDraftBillsFlow(sessionManager.getRestaurantId())
    }
}
