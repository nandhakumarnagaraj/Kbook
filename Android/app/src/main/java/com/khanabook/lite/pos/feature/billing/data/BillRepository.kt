package com.khanabook.lite.pos.feature.billing.data
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao

import androidx.work.WorkManager
import com.google.gson.Gson
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.printing.data.KotEventDao
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.billing.data.BillPaymentEntity
import com.khanabook.lite.pos.feature.printing.data.KotEventEntity
import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.billing.data.BillFinalizationOutcome
import com.khanabook.lite.pos.feature.billing.data.BillFinalizationResult
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.printing.data.KitchenPrintQueueRepository
import com.khanabook.lite.pos.feature.inventory.domain.InventoryConsumptionManager
import com.khanabook.lite.pos.feature.payments.domain.PaymentRecoveryAssessment
import com.khanabook.lite.pos.feature.payments.domain.PaymentSetValidator
import com.khanabook.lite.pos.feature.sync.domain.enqueueMasterSyncOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> terminalScopedFlow(
    restaurantIds: Flow<Long>,
    terminalScopes: Flow<String?>,
    query: (Long, String) -> Flow<List<T>>
): Flow<List<T>> =
    combine(restaurantIds, terminalScopes) { restaurantId, terminalId ->
        restaurantId to terminalId
    }
        .distinctUntilChanged()
        .flatMapLatest { (restaurantId, terminalId) ->
            if (restaurantId <= 0L || terminalId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                query(restaurantId, terminalId)
            }
        }

@OptIn(ExperimentalCoroutinesApi::class)
class BillRepository(
        private val billDao: BillDao,
        private val restaurantDao: com.khanabook.lite.pos.feature.auth.data.RestaurantDao,
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
    // terminal are mutable as operational records. Pulled bills (server_imported) are
    // read-only history and must never be mutated here.
    private fun isLocallyOwned(bill: BillEntity): Boolean =
        bill.recordScope == "terminal_operational" && bill.recordOrigin == "local_created"

    suspend fun insertFullBill(
            bill: BillEntity,
            items: List<BillItemEntity>,
            payments: List<BillPaymentEntity>,
            scheduleDurableSync: Boolean = true
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
        
        if (scheduleDurableSync) triggerBackgroundSync()
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

    suspend fun getRestorablePendingOnlineBillWithItems(id: Long): BillWithItems? =
        billDao.getRestorablePendingOnlineBillWithItems(
            id,
            sessionManager.getRestaurantId(),
            currentTerminalScope()
        )

    suspend fun updateBill(bill: BillEntity, scheduleDurableSync: Boolean = true) {
        // Terminal ownership isolation (defense-in-depth): only locally-owned operational
        // bills may be mutated. Server-imported history is read-only.
        if (!isLocallyOwned(bill)) return
        billDao.updateBill(bill)
        if (scheduleDurableSync) triggerBackgroundSync()
    }

    suspend fun addBillPayment(payment: BillPaymentEntity) {
        // Terminal ownership isolation: a payment may only be attached to a locally-owned
        // operational bill. Server-imported history must never receive a payment.
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
        payments: List<BillPaymentEntity>,
        scheduleDurableSync: Boolean = true
    ) {
        // Terminal ownership isolation (defense-in-depth).
        if (!isLocallyOwned(bill)) return
        billDao.settleDraftBill(bill, payments)
        if (scheduleDurableSync) triggerBackgroundSync()
    }

    suspend fun finalizeOnlineBill(
        billId: Long,
        payments: List<BillPaymentEntity>,
        completedAt: Long
    ): BillFinalizationResult {
        val finalized = billDao.finalizeOnlineBillAtomically(
            billId = billId,
            restaurantId = sessionManager.getRestaurantId(),
            terminalId = currentTerminalScope(),
            requestedPayments = payments,
            completedAt = completedAt
        )
        if (finalized.outcome == BillFinalizationOutcome.FINALIZED_NOW) {
            inventoryConsumptionManager?.consumeMaterialsForBill(finalized.billWithItems.items)
        }
        return finalized
    }

    suspend fun getPaymentRecoveryAssessment(billId: Long): PaymentRecoveryAssessment {
        val restaurantId = sessionManager.getRestaurantId()
        val bill = billDao.getOperationalBillById(
            billId,
            restaurantId,
            currentTerminalScope()
        ) ?: throw IllegalStateException("Bill is not locally editable on this terminal.")
        val payments = billDao.getActivePaymentsForBill(bill.id, restaurantId)
        return PaymentSetValidator.assessForRecovery(payments, bill.totalAmount)
    }

    suspend fun finalizeExistingPaymentSet(
        billId: Long,
        completedAt: Long
    ): BillFinalizationResult {
        val restaurantId = sessionManager.getRestaurantId()
        val payments = billDao.getActivePaymentsForBill(billId, restaurantId)
        return finalizeOnlineBill(billId, payments, completedAt)
    }

    suspend fun recoverPartialPayment(
        billId: Long,
        paymentMode: String,
        completedAt: Long
    ): BillFinalizationResult {
        val restaurantId = sessionManager.getRestaurantId()
        val bill = billDao.getOperationalBillById(
            billId,
            restaurantId,
            currentTerminalScope()
        ) ?: throw IllegalStateException("Bill is not locally editable on this terminal.")
        val identityBase = bill.operationId
            ?: bill.publicToken?.let { "bill:$it" }
            ?: "bill:${bill.id}"
        val finalized = billDao.recoverPartialPaymentAndFinalizeAtomically(
            billId = bill.id,
            restaurantId = restaurantId,
            terminalId = currentTerminalScope(),
            recoveryPayment = BillPaymentEntity(
                billId = bill.id,
                paymentMode = paymentMode,
                amount = "0.0",
                restaurantId = restaurantId,
                deviceId = bill.deviceId,
                terminalId = bill.terminalId,
                billPublicToken = bill.publicToken,
                operationId = "$identityBase:recovery:$paymentMode",
                createdAt = completedAt,
                updatedAt = completedAt
            ),
            completedAt = completedAt
        )
        if (finalized.outcome == BillFinalizationOutcome.FINALIZED_NOW) {
            inventoryConsumptionManager?.consumeMaterialsForBill(finalized.billWithItems.items)
        }
        return finalized
    }

    suspend fun resetUnverifiedPaymentRecovery(billId: Long): PaymentRecoveryAssessment {
        billDao.resetUnverifiedPaymentRecoveryAtomically(
            billId = billId,
            restaurantId = sessionManager.getRestaurantId(),
            terminalId = currentTerminalScope(),
            updatedAt = System.currentTimeMillis()
        )
        return getPaymentRecoveryAssessment(billId)
    }

    suspend fun getBillByDailyIdAndDate(displayId: String, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.core.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.core.util.DateUtils.getEndOfDay(date)
        return billDao.getBillByDailyIdAndDate(displayId, start, end, sessionManager.getRestaurantId(), currentTerminalScope())
    }

    suspend fun getBillByDailyIntIdAndDate(dailyId: Long, date: String): BillEntity? {
        val start = com.khanabook.lite.pos.core.util.DateUtils.getStartOfDay(date)
        val end = com.khanabook.lite.pos.core.util.DateUtils.getEndOfDay(date)
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
        val current = billDao.getBillById(id, restaurantId) ?: return

        val wasDeducted = current.orderStatus.equals("completed", ignoreCase = true) || 
                          current.orderStatus.equals("paid", ignoreCase = true)
        val isBecomingDeducted = status.equals("completed", ignoreCase = true) || 
                                 status.equals("paid", ignoreCase = true)
        val normalizedStatus = when {
            status.equals("completed", ignoreCase = true) || status.equals("paid", ignoreCase = true) -> "completed"
            status.equals("cancelled", ignoreCase = true) -> "cancelled"
            else -> "draft"
        }

        val newPaymentStatus = when (normalizedStatus) {
            "completed" -> "success"
            "cancelled" -> "failed"
            else -> "pending"
        }

        val now = System.currentTimeMillis()
        val newPaidAt = when (normalizedStatus) {
            "completed" -> current.paidAt ?: now
            "cancelled" -> null
            else -> null
        }

        if (isBecomingDeducted && !wasDeducted) {
            val existingPayments = billDao.getActivePaymentsForBill(id, restaurantId)
            val payableAmount = current.totalAmount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            if (existingPayments.isEmpty() && payableAmount > java.math.BigDecimal.ZERO) {
                val operationBase = "manual:status:$now"
                val payment = BillPaymentEntity(
                    billId = id,
                    paymentMode = current.paymentMode,
                    amount = current.totalAmount,
                    operationId = "$operationBase:payment:${current.paymentMode}:$id",
                    restaurantId = restaurantId,
                    deviceId = current.deviceId,
                    terminalId = current.terminalId,
                    billPublicToken = current.publicToken,
                    verifiedBy = "manual",
                    isSynced = false,
                    updatedAt = now
                )
                billDao.insertBillPayments(listOf(payment))
            } else if (existingPayments.isNotEmpty()) {
                val repaired = existingPayments.mapIndexed { idx, p ->
                    if (p.operationId.isNullOrBlank()) {
                        p.copy(
                            operationId = "manual:payment:${p.paymentMode}:${id}:${idx}:$now",
                            verifiedBy = p.verifiedBy ?: "manual",
                            isSynced = false,
                            updatedAt = now
                        )
                    } else p
                }
                billDao.updateBillPayments(repaired)
            }
        }

        // Single authoritative write: the full-entity write covers orderStatus,
        // paymentStatus, paidAt, statusVersion bump, isSynced=false and updatedAt in
        // one statement. The previous follow-up targeted queries (updateOrderStatus /
        // updatePaymentStatus) re-wrote the same fields non-atomically — the extra
        // version bump also double-incremented status_version per edit — and left a
        // window where a concurrent push snapshot/ack could observe a half-mutated
        // row and mark it synced on a stale fingerprint.
        billDao.updateBill(
            current.copy(
                orderStatus = normalizedStatus,
                paymentStatus = newPaymentStatus,
                paidAt = newPaidAt,
                statusVersion = current.statusVersion + 1,
                isSynced = false,
                updatedAt = now
            )
        )

        if (isBecomingDeducted && !wasDeducted) {
            val billWithItems = billDao.getBillWithItemsById(id, restaurantId)
            billWithItems?.let { inventoryConsumptionManager?.consumeMaterialsForBill(it.items) }
        }
        triggerBackgroundSync()
    }

    suspend fun cancelOrder(id: Long, reason: String, scheduleDurableSync: Boolean = true) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = billDao.getBillById(id, restaurantId) ?: return
        // Snapshot BEFORE the status flips to 'cancelled' — the CANCEL event must
        // capture the order exactly as the kitchen last knew it.
        val before = billDao.getBillWithItemsById(id, restaurantId)
        billDao.cancelBill(id, reason, System.currentTimeMillis(), restaurantId)
        kitchenPrintQueueRepository?.deleteByBillId(id)
        // Kitchen cancellation notice: only when the kitchen has ALREADY received the
        // order (≥1 printed KOT event). Records a CANCEL event whose full-order snapshot
        // prints/is displayed as "*** ORDER CANCELLED ***". Orders the kitchen never
        // saw get no notice — they never knew the order existed.
        val publicToken = current.publicToken?.takeIf { it.isNotBlank() }
        if (publicToken != null && isKitchenPrintableStatus(current.orderStatus)) {
            val hasPrinted = kotEventDao.getEventsForBill(publicToken).any { it.isPrinted }
            if (hasPrinted) {
                val items = before?.items?.filter { !it.isDeleted } ?: emptyList()
                if (items.isNotEmpty()) {
                    recordKotEvent(before!!.bill, KotEventType.CANCEL, items)
                }
            }
        }
        if (scheduleDurableSync) triggerBackgroundSync()
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
        val restaurantId = sessionManager.getRestaurantId()
        val current = billDao.getBillById(id, restaurantId) ?: return
        if (current.orderStatus.equals("cancelled", ignoreCase = true)) return
        val normalizedMode = mode.lowercase()
        billDao.updateBill(
            current.copy(
                paymentMode = normalizedMode,
                partAmount1 = partAmount1,
                partAmount2 = partAmount2,
                statusVersion = current.statusVersion + 1,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        // Keep active bill_payments in sync if records exist
        val activePayments = billDao.getActivePaymentsForBill(id, restaurantId)
        if (activePayments.isNotEmpty()) {
            if (activePayments.size == 1) {
                val p = activePayments.first()
                billDao.updateBillPayments(
                    listOf(
                        p.copy(
                            paymentMode = normalizedMode,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                )
            }
        }
        triggerBackgroundSync()
    }

    suspend fun updatePaymentStatus(id: Long, status: String) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = billDao.getBillById(id, restaurantId) ?: return
        billDao.updateBill(
            current.copy(
                paymentStatus = status.lowercase(),
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
               com.khanabook.lite.pos.core.util.DateUtils.getStartOfDay(startDate)
            }
        } catch (e: Exception) { 0L }
        
        val endMillis = try {
            if (endDate.contains(":")) {
               java.time.LocalDateTime.parse(endDate.replace(" ", "T")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
               com.khanabook.lite.pos.core.util.DateUtils.getEndOfDay(endDate)
            }
        } catch (e: Exception) { Long.MAX_VALUE }
        
        return getBillsByDateRange(startMillis, endMillis)
    }

    fun getBillsByDateRange(startMillis: Long, endMillis: Long): Flow<List<BillEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getBillsByDateRange(startMillis, endMillis, restaurantId, currentTerminalScope())
        }
    }

    fun getShopBillsByDateRange(startMillis: Long, endMillis: Long): Flow<List<BillEntity>> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getShopBillsByDateRange(startMillis, endMillis, restaurantId)
        }
    }

    fun getProfileFlow(): Flow<com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity?> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            if (restaurantId > 0) restaurantDao.getProfileFlow(restaurantId) else restaurantDao.getProfileFlow()
        }
    }

    fun getUnsyncedCount(): Flow<Int> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            billDao.getUnsyncedCount(restaurantId)
        }
    }

    suspend fun getTopSellingItemsInRange(startMillis: Long, endMillis: Long, limit: Int): List<com.khanabook.lite.pos.feature.reports.domain.TopSellingItem> {
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

    fun getActiveDraftBillsFlow(): Flow<List<BillEntity>> =
        terminalScopedFlow(sessionManager.restaurantId, sessionManager.terminalScope) { restaurantId, terminalId ->
            billDao.getActiveDraftBillsFlow(restaurantId, terminalId)
        }

    fun getActionableDraftBillsWithItemsFlow(): Flow<List<BillWithItems>> =
        terminalScopedFlow(sessionManager.restaurantId, sessionManager.terminalScope) { restaurantId, terminalId ->
            billDao.getActionableDraftBillsWithItemsFlow(restaurantId, terminalId)
        }

    private suspend fun recordKotEvent(
        bill: BillEntity,
        eventType: String,
        items: List<BillItemEntity>
    ) {
        val publicToken = bill.publicToken?.takeIf { it.isNotBlank() } ?: return
        if (items.isEmpty()) return
        // KOT events are keyed on the owning TERMINAL (not the physical deviceId) so a
        // recovered/replaced device keeps firing KOT for its own in-progress bills.
        val localTerminalId = sessionManager.getTerminalId()
        val billOwnerTerminal = bill.currentOwnerTerminalId
            ?.takeIf { it.isNotBlank() }
            ?: bill.createdTerminalId?.takeIf { it.isNotBlank() }
            ?: bill.terminalId?.takeIf { it.isNotBlank() }
        if (localTerminalId != null &&
            billOwnerTerminal != null &&
            billOwnerTerminal != localTerminalId
        ) return
        if (!isKitchenPrintableStatus(bill.orderStatus)) return

        val revision = (kotEventDao.getMaxRevisionForBill(publicToken) + 1L).toString()
        kotEventDao.insert(
            KotEventEntity(
                publicToken = publicToken,
                kotRevision = revision,
                eventType = eventType,
                itemSnapshotJson = serializeKotItems(items),
                originatingDeviceId = sessionManager.getDeviceId(),
                isPrinted = false,
                createdAt = System.currentTimeMillis()
            )
        )
        // VOID events have no caller-side print dispatch (unlike NEW/ADD which route through
        // PrintRouter). Enqueue a VOID ticket so the kitchen learns what was scrubbed from the
        // order. Skip when another ticket for this bill is already pending — the queue is keyed
        // (billId, printerMac) so a second unassigned job would clobber the first; kot_events
        // still holds the audit record.
        if (eventType == KotEventType.VOID || eventType == KotEventType.CANCEL) {
            kitchenPrintQueueRepository
                ?.takeIf { !it.hasPendingForBill(bill.id) }
                ?.enqueuePending(
                    billId = bill.id,
                    printerMac = KitchenPrintQueueRepository.UNASSIGNED_PRINTER_MAC,
                    error = if (eventType == KotEventType.CANCEL) "Cancel KOT" else "Void KOT",
                    publicToken = publicToken,
                    kotRevision = revision
                )
        }
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
