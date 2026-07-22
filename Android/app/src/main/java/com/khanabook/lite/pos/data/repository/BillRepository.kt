package com.khanabook.lite.pos.data.repository

import android.util.Log
import androidx.work.WorkManager
import com.google.gson.Gson
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.KotEventDao
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.BillItemEntity
import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.data.local.entity.KotEventEntity
import com.khanabook.lite.pos.data.local.entity.KotEventType
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
        private val kotEventDao: KotEventDao,
        private val sessionManager: SessionManager
) {
    private val gson = Gson()

    private fun currentTerminalScope(): String =
        sessionManager.getTerminalId()
            ?: sessionManager.getTerminalSeries()
            ?: "LEGACY_UNRESOLVED"

    // Terminal ownership isolation (defense-in-depth): only bills created locally on this
    // terminal are mutable as operational records. Pulled bills (server_imported /
    // marketplace_imported) are read-only history and must never be mutated here.
    private fun isLocallyOwned(bill: BillEntity): Boolean =
        bill.recordScope == "terminal_operational" && bill.recordOrigin == "local_created"

    suspend fun insertFullBill(
            bill: BillEntity,
            items: List<BillItemEntity>,
            payments: List<BillPaymentEntity>
    ): Long {
        val billId = billDao.insertFullBill(bill, items, payments)
        billDao.getBillWithItemsById(billId, sessionManager.getRestaurantId())?.let {
            recordKotEvent(it.bill, KotEventType.NEW, it.items.filter { item -> !item.isDeleted })
        }
        
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
        // Terminal ownership isolation (defense-in-depth): only locally-owned operational
        // bills may be mutated. Server-imported / marketplace-imported history is read-only.
        if (!isLocallyOwned(bill)) return
        billDao.updateBill(bill)
        triggerBackgroundSync()
    }

    suspend fun addBillPayment(payment: BillPaymentEntity) {
        // Terminal ownership isolation: a payment may only be attached to a locally-owned
        // operational bill. Server-imported / marketplace history must never receive a payment.
        val parent = billDao.getOperationalBillById(payment.billId, sessionManager.getRestaurantId(), currentTerminalScope())
            ?: return
        if (!isLocallyOwned(parent)) return
        billDao.insertBillPayment(
            payment.copy(
                restaurantId = parent.restaurantId,
                deviceId = parent.deviceId,
                terminalId = parent.terminalId,
                billPublicToken = parent.publicToken,
                operationId = payment.operationId ?: parent.operationId
            )
        )
        triggerBackgroundSync()
    }

    suspend fun addBillPayments(payments: List<BillPaymentEntity>) {
        val restaurantId = sessionManager.getRestaurantId()
        val terminalId = currentTerminalScope()
        val parents = billDao.getBillsByIds(payments.map { it.billId }.distinct(), restaurantId).associateBy { it.id }
        // Drop payments whose parent is not a locally-owned operational bill.
        val allowed = payments.filter { parents[it.billId]?.let { p -> isLocallyOwned(p) } == true }
        if (allowed.isEmpty()) return
        billDao.insertBillPayments(
            allowed.map { payment ->
                val parent = parents[payment.billId]!!
                payment.copy(
                    restaurantId = parent.restaurantId,
                    deviceId = parent.deviceId,
                    terminalId = parent.terminalId,
                    billPublicToken = parent.publicToken,
                    operationId = payment.operationId ?: parent.operationId
                )
            }
        )
        triggerBackgroundSync()
    }

    suspend fun settleDraftBill(
        bill: BillEntity,
        payments: List<BillPaymentEntity>
    ) {
        // Terminal ownership isolation (defense-in-depth).
        if (!isLocallyOwned(bill)) return
        billDao.settleDraftBill(bill, payments)
        triggerBackgroundSync()
    }

    suspend fun getBillByDailyIdAndDate(displayId: String, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(date)
        return billDao.getBillByDailyIdAndDate(displayId, start, end, sessionManager.getRestaurantId(), currentTerminalScope())
    }

    suspend fun getBillByDailyIntIdAndDate(dailyId: Long, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(date)
        return billDao.getBillByDailyIntIdAndDate(dailyId, start, end, sessionManager.getRestaurantId(), currentTerminalScope())
    }

    fun getDraftBills(): Flow<List<BillEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getDraftBills(restaurantId, currentTerminalScope())
        }
    }

    suspend fun getLatestPendingOnlineBill(): BillEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        val activeUserId = sessionManager.getActiveUserId()
        val terminalId = currentTerminalScope()
        return if (activeUserId != null) {
            billDao.getLatestPendingOnlineBill(restaurantId, activeUserId, terminalId)
                ?: billDao.getLatestPendingOnlineBill(restaurantId, terminalId)
        } else {
            billDao.getLatestPendingOnlineBill(restaurantId, terminalId)
        }
    }

    fun getPendingOnlineBillsFlow(): Flow<List<BillEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getPendingOnlineBillsFlow(restaurantId, currentTerminalScope())
        }
    }

    fun getSyncQuarantineCountFlow(): Flow<Int> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getSyncQuarantineCountFlow(restaurantId)
        }
    }

    suspend fun updateOrderStatus(id: Long, status: String) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = billDao.getOperationalBillById(id, restaurantId, currentTerminalScope()) ?: return
        if (!isLocallyOwned(current)) return

        
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
        val current = billDao.getOperationalBillById(id, sessionManager.getRestaurantId(), currentTerminalScope()) ?: return
        if (!isLocallyOwned(current)) return
        billDao.cancelBill(id, reason, System.currentTimeMillis(), sessionManager.getRestaurantId())
        kitchenPrintQueueRepository?.deleteByBillId(id)
        triggerBackgroundSync()
    }

    suspend fun cancelStalePendingOnlineDrafts(): Int {
        val cancelled = billDao.cancelStalePendingOnlineDrafts(
            reason = "Superseded by new payment attempt",
            updatedAt = System.currentTimeMillis(),
            restaurantId = sessionManager.getRestaurantId(),
            terminalId = currentTerminalScope()
        )
        if (cancelled > 0) triggerBackgroundSync()
        return cancelled
    }

    suspend fun updatePaymentMode(id: Long, mode: String, partAmount1: String = "0.0", partAmount2: String = "0.0") {
        val currTerminal = currentTerminalScope()
        val restaurantId = sessionManager.getRestaurantId()
        val current = billDao.getOperationalBillById(id, restaurantId, currTerminal)
        Log.d("BillRepo", "updatePaymentMode: id=$id mode=$mode terminal=$currTerminal restaurantId=$restaurantId bill=$current")
        if (current == null) {
            Log.d("BillRepo", "updatePaymentMode: getOperationalBillById returned null — checking raw bill")
            val rawBill = billDao.getBillById(id, restaurantId)
            Log.d("BillRepo", "updatePaymentMode: rawBill=$rawBill recordOrigin=${rawBill?.recordOrigin} recordScope=${rawBill?.recordScope} createdTerminalId=${rawBill?.createdTerminalId} currentOwnerTerminalId=${rawBill?.currentOwnerTerminalId} isSynced=${rawBill?.isSynced}")
            return
        }
        if (!isLocallyOwned(current)) { Log.d("BillRepo", "updatePaymentMode: !isLocallyOwned scope=${current.recordScope} origin=${current.recordOrigin}"); return }
        if (current.orderStatus.equals("cancelled", ignoreCase = true)) { Log.d("BillRepo", "updatePaymentMode: cancelled"); return }
        billDao.updateBill(
            current.copy(
                paymentMode = mode,
                partAmount1 = partAmount1,
                partAmount2 = partAmount2,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        Log.d("BillRepo", "updatePaymentMode: updated successfully to mode=$mode")
        triggerBackgroundSync()
    }

    suspend fun updatePaymentStatus(id: Long, status: String) {
        val current = billDao.getOperationalBillById(id, sessionManager.getRestaurantId(), currentTerminalScope()) ?: return
        if (!isLocallyOwned(current)) return
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
            billDao.getBillsByDateRange(startMillis, endMillis, restaurantId, currentTerminalScope())
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
        return billDao.getTopSellingItemsInRange(startMillis, endMillis, limit, sessionManager.getRestaurantId(), currentTerminalScope())
    }

    suspend fun getRecentCustomers(limit: Int = 5): List<Pair<String, String>> {
        return billDao.getRecentBillsWithCustomers(sessionManager.getRestaurantId(), currentTerminalScope())
            .distinctBy { it.customerWhatsapp }
            .take(limit)
            .mapNotNull { bill ->
                val phone = bill.customerWhatsapp ?: return@mapNotNull null
                phone to (bill.customerName ?: "")
            }
    }

    suspend fun getRecentDineInCustomers(limit: Int = 5): List<Pair<String, String>> {
        return billDao.getRecentDineInBillsWithCustomers(sessionManager.getRestaurantId(), currentTerminalScope())
            .distinctBy { it.customerName }
            .take(limit)
            .mapNotNull { bill ->
                val name = bill.customerName ?: return@mapNotNull null
                (bill.customerWhatsapp ?: "") to name
            }
    }

    // New GST invoice-number lookup (e.g. "26A1-000042").
    suspend fun getBillWithItemsByInvoiceNumber(invoiceNumber: String): BillWithItems? {
        return billDao.getBillWithItemsByInvoiceNumber(invoiceNumber, sessionManager.getRestaurantId(), currentTerminalScope())
    }

    suspend fun getMaxInvoiceSequence(invoiceSeries: String): Long {
        return billDao.getMaxInvoiceSequence(
            restaurantId = sessionManager.getRestaurantId(),
            invoiceSeries = invoiceSeries
        )
    }

    // Legacy fallback: bills issued before the new numbering used lifetime_order_id (e.g. "INV42").
    suspend fun getBillWithItemsByLegacyInvoiceNo(invoiceNo: Long): BillWithItems? {
        return billDao.getBillByLifetimeNo(invoiceNo, sessionManager.getRestaurantId(), currentTerminalScope())?.let { bill ->
            billDao.getBillWithItemsById(bill.id, sessionManager.getRestaurantId())
        }
    }

    suspend fun getBillsWithPendingKds(): List<BillWithItems> {
        return billDao.getBillsWithPendingKds(sessionManager.getRestaurantId(), currentTerminalScope()).mapNotNull { bill ->
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
        val restaurantId = sessionManager.getRestaurantId()
        val terminalId = currentTerminalScope()
        // Only insert items into locally-owned operational bills. History bills are read-only.
        val parents = billDao.getBillsByIds(items.map { it.billId }.distinct(), restaurantId).associateBy { it.id }
        val allowed = items.filter { parents[it.billId]?.let { p -> isLocallyOwned(p) } == true }
        if (allowed.isEmpty()) return
        billDao.insertBillItems(allowed)
        allowed.groupBy { it.billId }.forEach { (billId, insertedItems) ->
            parents[billId]?.let { bill ->
                recordKotEvent(bill, KotEventType.ADD, insertedItems.filter { !it.isDeleted })
            }
        }
        triggerBackgroundSync()
    }

    suspend fun updateBillItem(item: BillItemEntity) {
        val restaurantId = sessionManager.getRestaurantId()
        val bill = billDao.getOperationalBillById(item.billId, restaurantId, currentTerminalScope()) ?: return
        if (!isLocallyOwned(bill)) return
        val existing = billDao.getBillItemsByIds(listOf(item.id), restaurantId).firstOrNull()
        billDao.updateBillItem(item)
        if (existing != null) {
            when {
                item.quantity > existing.quantity ->
                    recordKotEvent(bill, KotEventType.ADD, listOf(item.copy(quantity = item.quantity - existing.quantity)))
                item.quantity < existing.quantity ->
                    recordKotEvent(bill, KotEventType.VOID, listOf(existing.copy(quantity = existing.quantity - item.quantity)))
            }
        }
        triggerBackgroundSync()
    }

    suspend fun deleteBillItemById(id: Long) {
        val restaurantId = sessionManager.getRestaurantId()
        val existing = billDao.getBillItemsByIds(listOf(id), restaurantId).firstOrNull()
        val bill = existing?.let { billDao.getOperationalBillById(it.billId, restaurantId, currentTerminalScope()) }
        if (existing != null && bill != null && isLocallyOwned(bill)) {
            recordKotEvent(bill, KotEventType.VOID, listOf(existing))
            billDao.deleteBillItemById(id)
            triggerBackgroundSync()
        }
    }

    fun getActiveDraftBillsFlow(): kotlinx.coroutines.flow.Flow<List<BillEntity>> {
        return billDao.getActiveDraftBillsFlow(sessionManager.getRestaurantId(), currentTerminalScope())
    }

    private suspend fun recordKotEvent(
        bill: BillEntity,
        eventType: String,
        items: List<BillItemEntity>
    ) {
        val publicToken = bill.publicToken?.takeIf { it.isNotBlank() } ?: return
        if (items.isEmpty()) return
        val localDeviceId = sessionManager.getDeviceId()
        if (!bill.deviceId.isNullOrBlank() && bill.deviceId != localDeviceId) return
        if (!isKitchenPrintableStatus(bill.orderStatus)) return

        val revision = (kotEventDao.getMaxRevisionForBill(publicToken) + 1L).toString()
        kotEventDao.insert(
            KotEventEntity(
                publicToken = publicToken,
                kotRevision = revision,
                eventType = eventType,
                itemSnapshotJson = serializeKotItems(items),
                originatingDeviceId = localDeviceId,
                isPrinted = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun isKitchenPrintableStatus(status: String): Boolean =
        status.equals("draft", ignoreCase = true) ||
            status.equals("completed", ignoreCase = true) ||
            status.equals("paid", ignoreCase = true)

    private fun serializeKotItems(items: List<BillItemEntity>): String {
        return gson.toJson(items.map { item ->
            mapOf(
                "id" to item.id,
                "menuItemId" to item.menuItemId,
                "itemName" to item.itemName,
                "variantId" to item.variantId,
                "variantName" to item.variantName,
                "price" to item.price,
                "quantity" to item.quantity,
                "itemTotal" to item.itemTotal,
                "specialInstruction" to item.specialInstruction
            )
        })
    }
}
