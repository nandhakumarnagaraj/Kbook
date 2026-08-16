package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.domain.util.AppConstants

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.repository.MenuRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.OrderIdManager
import com.khanabook.lite.pos.domain.manager.PaymentRecoveryAssessment
import com.khanabook.lite.pos.domain.manager.PrintDispatchMode
import com.khanabook.lite.pos.domain.manager.PrintRouter
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BillingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val billRepository: BillRepository,
    private val menuRepository: MenuRepository,
    private val restaurantRepository: RestaurantRepository,
    private val printerProfileRepository: PrinterProfileRepository,
    private val kitchenPrintQueueRepository: KitchenPrintQueueRepository,
    private val sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
    private val syncManager: com.khanabook.lite.pos.domain.manager.SyncManager,
    private val printRouter: PrintRouter,
    val printerManager: com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager,
    private val networkMonitor: com.khanabook.lite.pos.domain.util.NetworkMonitor,
    private val permissionManager: com.khanabook.lite.pos.domain.manager.PermissionManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private data class InvoiceIdentity(
        val financialYear: String,
        val invoiceSeries: String,
        val invoiceSequence: Long,
        val invoiceNumber: String
    )

    private suspend fun allocateInvoiceIdentity(createdAt: Long): InvoiceIdentity? {
        val terminalSeries = sessionManager.getTerminalSeries()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val displaySeries = terminalSeries.first().uppercaseChar().toString()
        val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
        val date = java.time.Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate()
        val financialYearStart = if (date.monthValue >= 4) date.year else date.year - 1
        val financialYear = (financialYearStart % 100).toString().padStart(2, '0')
        val invoiceSeries = "$financialYear$terminalSeries"
        val sequence = billRepository.getMaxInvoiceSequence(invoiceSeries) + 1L
        return InvoiceIdentity(
            financialYear = financialYear,
            invoiceSeries = invoiceSeries,
            invoiceSequence = sequence,
            invoiceNumber = "$displaySeries${sequence.toString().padStart(2, '0')}"
        )
    }

    private fun requireActiveTerminalIdentity(): TerminalIdentity? {
        val identity = sessionManager.getTerminalIdentity()
        if (identity == null || !identity.isActive || identity.terminalSeries.isBlank()) {
            _error.value = "Terminal is not ready. Sync terminal setup and try again."
            return null
        }
        return identity
    }

    val connectionStatus: StateFlow<com.khanabook.lite.pos.domain.util.ConnectionStatus> =
        networkMonitor.status.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            com.khanabook.lite.pos.domain.util.ConnectionStatus.Unavailable
        )

    companion object {
        private const val TAG = "BillingViewModel"
        private const val PENDING_ONLINE_BILL_ID = "pending_online_bill_id"
    }

    private val orderMutex = Mutex()

    // ── Extracted managers ──────────────────────────────────────────────────

    private val gson = com.google.gson.Gson()

    private val cartManager = CartManager(
        menuRepository = menuRepository,
        initialItems = savedStateHandle.get<String>("cart_items")?.let { json ->
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<CartItem>>() {}.type
                gson.fromJson<List<CartItem>>(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore cart items", e)
                emptyList()
            }
        } ?: emptyList()
    )

    private val paymentStateManager = PaymentStateManager()

    private val printCoordinator = PrintCoordinator(
        appContext = appContext,
        printRouter = printRouter,
        kitchenPrintQueueRepository = kitchenPrintQueueRepository
    )

    // ── Delegated flows (public API preserved) ──────────────────────────────

    // Cache the restaurant profile reactively — avoids repeated DB reads in updateSummary
    // and completeOrder. Stays automatically fresh because it's backed by a Flow.
    private val _cachedProfile: StateFlow<RestaurantProfileEntity?> =
        restaurantRepository.getProfileFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val cachedProfile: StateFlow<RestaurantProfileEntity?> get() = _cachedProfile

    val cartItems: StateFlow<List<CartItem>> = cartManager.cartItems
    val billSummary: StateFlow<BillSummary> = cartManager.billSummary

    val paymentMode: StateFlow<PaymentMode> = paymentStateManager.paymentMode
    val partAmount1: StateFlow<String> = paymentStateManager.partAmount1
    val partAmount2: StateFlow<String> = paymentStateManager.partAmount2
    val persistedPaymentTotal: StateFlow<String?> = paymentStateManager.persistedPaymentTotal
    val paymentRecovery: StateFlow<PaymentRecoveryAssessment> = paymentStateManager.paymentRecovery

    val printStatus: StateFlow<String?> = printCoordinator.printStatus
    val receiptPrinting: StateFlow<Boolean> = printCoordinator.receiptPrinting
    val kitchenPrinting: StateFlow<Boolean> = printCoordinator.kitchenPrinting

    private val _customerName = MutableStateFlow(savedStateHandle.get<String>("customer_name") ?: "")
    val customerName: StateFlow<String> = _customerName

    private val _customerWhatsapp = MutableStateFlow(savedStateHandle.get<String>("customer_whatsapp") ?: "")
    val customerWhatsapp: StateFlow<String> = _customerWhatsapp

    private val _recentCustomers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val recentCustomers: StateFlow<List<Pair<String, String>>> = _recentCustomers

    private val _recentDineInCustomers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val recentDineInCustomers: StateFlow<List<Pair<String, String>>> = _recentDineInCustomers

    fun loadRecentCustomers() {
        viewModelScope.launch {
            _recentCustomers.value = billRepository.getRecentCustomers()
        }
    }

    fun loadRecentDineInCustomers() {
        viewModelScope.launch {
            _recentDineInCustomers.value = billRepository.getRecentDineInCustomers()
        }
    }

    private val _lastBill = MutableStateFlow<BillWithItems?>(null)
    val lastBill: StateFlow<BillWithItems?> = _lastBill

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var editingBillId: Long? = null

    private val _orderType = MutableStateFlow("dine_in")
    val orderType: StateFlow<String> = _orderType

    fun setOrderType(type: String) {
        _orderType.value = type
    }

    val activeDraftBillsFlow: Flow<List<BillEntity>> = billRepository.getActiveDraftBillsFlow()

    fun setGatewayResult(txnId: String?, status: String?) {
        paymentStateManager.setGatewayResult(txnId, status)
    }

    fun clearGatewayResult() {
        paymentStateManager.clearGatewayResult()
    }

    private fun validatePaymentLimits(
        total: String,
        mode: PaymentMode,
        partAmount1: String,
        partAmount2: String
    ): Boolean {
        val errorMsg = paymentStateManager.validatePaymentLimits(total, mode, partAmount1, partAmount2)
        if (errorMsg != null) {
            _error.value = errorMsg
            return false
        }
        return true
    }

    init {
        // Process death protection save state flows
        viewModelScope.launch {
            _customerName.collect { name ->
                savedStateHandle["customer_name"] = name
            }
        }
        viewModelScope.launch {
            _customerWhatsapp.collect { whatsapp ->
                savedStateHandle["customer_whatsapp"] = whatsapp
            }
        }
        viewModelScope.launch {
            cartManager.cartItems.collect { items ->
                try {
                    val json = gson.toJson(items)
                    savedStateHandle["cart_items"] = json
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save cart items", e)
                }
            }
        }

        combine(cartManager.cartItems, _cachedProfile) { items, profile ->
            cartManager.computeSummary(items, profile)
        }
            .onEach { summary -> cartManager.setSummary(summary) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            printRouter.printResults.collect { (billId, result) ->
                val lastBillId = _lastBill.value?.bill?.id
                if (lastBillId == billId) {
                    val errorMsg = printCoordinator.getAutoPrintError(result)
                    if (errorMsg != null) {
                        _error.value = errorMsg
                    }
                    printCoordinator.handleAutoPrintResult(billId, result)
                }
            }
        }
    }

    fun addToCart(item: MenuItemEntity, variant: ItemVariantEntity? = null) {
        viewModelScope.launch {
            val errorMsg = cartManager.addToCart(item, variant)
            if (errorMsg != null) {
                _error.value = errorMsg
            }
        }
    }

    fun removeFromCart(item: MenuItemEntity, variant: ItemVariantEntity? = null) {
        cartManager.removeFromCart(item, variant)
    }

    fun handleScannedBarcode(barcode: String) {
        viewModelScope.launch {
            val errorMsg = cartManager.handleScannedBarcode(barcode)
            if (errorMsg != null) {
                _error.value = errorMsg
            }
        }
    }

    fun addItemByScannedText(text: String) {
        viewModelScope.launch {
            cartManager.addItemByScannedText(text)
        }
    }

    private fun BillItemEntity.toRestorableMenuItem(): MenuItemEntity =
        MenuItemEntity(
            id = menuItemId ?: -id,
            categoryId = 0,
            name = itemName,
            basePrice = price,
            isAvailable = false,
            restaurantId = restaurantId,
            deviceId = deviceId
        )

    private fun BillItemEntity.toRestorableVariant(menuItemLocalId: Long): ItemVariantEntity? {
        val name = variantName?.takeIf { it.isNotBlank() } ?: return null
        return ItemVariantEntity(
            id = variantId ?: -id,
            menuItemId = menuItemLocalId,
            variantName = name,
            price = price,
            isAvailable = false,
            restaurantId = restaurantId,
            deviceId = deviceId
        )
    }

    fun setCustomerInfo(name: String, whatsapp: String) {
        _customerName.value = name
        _customerWhatsapp.value = whatsapp
    }

    fun resetForNewBill() {
        invalidateRestoration()
        cartManager.clear()
        _customerName.value = ""
        _customerWhatsapp.value = ""
        _orderType.value = "dine_in"
        paymentStateManager.reset()
        _lastBill.value = null
        _error.value = null
        printCoordinator.clearStatus()
        savedStateHandle[PENDING_ONLINE_BILL_ID] = null
    }

    fun setPaymentMode(mode: PaymentMode, p1: String = "0.0", p2: String = "0.0") {
        paymentStateManager.setPaymentMode(mode, p1, p2)
    }

    suspend fun getBillById(localBillId: Long) = billRepository.getBillWithItemsById(localBillId)

    suspend fun triggerSyncAndWait() {
        syncManager.performFullSync()
    }

    suspend fun getLatestPendingOnlineBillId(): Long? {
        var billId = savedStateHandle.get<Long>(PENDING_ONLINE_BILL_ID)
        // If the saved handle is empty (e.g. after a ViewModel recreation via deep-link
        // navigation), fall back to a DB query for the latest pending draft on this
        // terminal. This ensures UPI deep-link resume works even when the original
        // ViewModel's saved state is no longer reachable.
        if (billId == null) {
            val pending = billRepository.getLatestPendingOnlineBill()
            if (pending != null) {
                billId = pending.id
                savedStateHandle[PENDING_ONLINE_BILL_ID] = billId
            }
        }
        if (billId == null) return null
        val capturedGeneration = invalidateRestoration()
        val bill = billRepository.getRestorablePendingOnlineBillWithItems(billId)
        if (bill == null) {
            if (ownsRestorationAttempt(capturedGeneration)) {
                clearInvalidPendingRestoration(billId)
            }
            return null
        }
        return bill?.bill?.id
    }

    suspend fun cancelPendingOnlineDrafts(): Int {
        return billRepository.cancelStalePendingOnlineDrafts()
    }

    suspend fun restorePendingOnlineBill(localBillId: Long): Boolean {
        val capturedGeneration = invalidateRestoration()
        val billWithItems = billRepository.getRestorablePendingOnlineBillWithItems(localBillId)
            ?: run {
                if (ownsRestorationAttempt(capturedGeneration)) {
                    clearInvalidPendingRestoration(localBillId)
                }
                return false
            }
        val bill = billWithItems.bill
        val paymentRecovery = billRepository.getPaymentRecoveryAssessment(localBillId)
        val restoredItems = billWithItems.items.map { billItem ->
            val menuItem = menuRepository.getItemById(billItem.menuItemId ?: 0L)
                ?: billItem.toRestorableMenuItem()
            val variant = billItem.variantId?.let { menuRepository.getVariantById(it) }
                ?: billItem.toRestorableVariant(menuItem.id)
            CartItem(
                item = menuItem,
                variant = variant,
                quantity = billItem.quantity,
                note = billItem.specialInstruction ?: ""
            )
        }

        // Stale response — a newer session has started. Do not mutate state.
        if (!ownsRestorationAttempt(capturedGeneration)) return false

        savedStateHandle[PENDING_ONLINE_BILL_ID] = localBillId
        editingBillId = localBillId
        _lastBill.value = billWithItems
        _customerName.value = bill.customerName ?: ""
        _customerWhatsapp.value = bill.customerWhatsapp ?: ""
        _orderType.value = bill.orderType
        paymentStateManager.setPaymentMode(
            PaymentMode.fromDbValue(bill.paymentMode),
            bill.partAmount1,
            bill.partAmount2
        )
        paymentStateManager.setPersistedPaymentTotal(bill.totalAmount)
        paymentStateManager.setPaymentRecovery(paymentRecovery)
        cartManager.setItems(restoredItems)
        return true
    }

    // ── Generation-safe restoration ────────────────────────────────────────

    private var restorationGeneration = 0L

    /**
     * Increments restoration generation to invalidate any in-flight restoration.
     * Returns the new generation token.
     */
    private fun invalidateRestoration(): Long {
        restorationGeneration += 1
        return restorationGeneration
    }

    /**
     * Checks whether a captured generation still owns the restoration attempt.
     */
    private fun ownsRestorationAttempt(capturedGeneration: Long): Boolean =
        capturedGeneration == restorationGeneration

    private fun clearInvalidPendingRestoration(localBillId: Long) {
        if (savedStateHandle.get<Long>(PENDING_ONLINE_BILL_ID) == localBillId) {
            savedStateHandle[PENDING_ONLINE_BILL_ID] = null
        }
        clearGatewayResult()
        editingBillId = null
        _lastBill.value = null
        cartManager.clear()
        _customerName.value = ""
        _customerWhatsapp.value = ""
        paymentStateManager.setPaymentMode(PaymentMode.CASH, "0.0", "0.0")
        paymentStateManager.setPersistedPaymentTotal(null)
        paymentStateManager.setPaymentRecovery(PaymentRecoveryAssessment.Empty)
        Log.w(TAG, "Rejected pending online bill restoration for localBillId=$localBillId")
    }

    suspend fun createDraftOnlineBill(): Long? = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            if (cartManager.currentItems.isEmpty()) {
                _error.value = "Add at least one item before starting payment."
                return@withLock null
            }
            _isLoading.value = true
            try {
                val existingPendingId = savedStateHandle.get<Long>(PENDING_ONLINE_BILL_ID)
                val existingPending = existingPendingId?.let {
                    billRepository.getRestorablePendingOnlineBillWithItems(it)?.bill
                }
                if (existingPending?.orderStatus == OrderStatus.DRAFT.dbValue &&
                    existingPending.paymentStatus == PaymentStatus.PENDING.dbValue
                ) {
                    _isLoading.value = false
                    return@withLock existingPending.id
                }
                if (existingPendingId != null) {
                    savedStateHandle[PENDING_ONLINE_BILL_ID] = null
                    clearGatewayResult()
                    Log.w(TAG, "Discarded ineligible pending online bill id before explicit creation")
                }

                // Cancel any stale DRAFT+PENDING bills from previous failed attempts before
                // creating a new one — prevents duplicate drafts from accumulating.
                billRepository.cancelStalePendingOnlineDrafts()

                val profile = _cachedProfile.value ?: restaurantRepository.getProfile()
                if (profile == null) {
                    _isLoading.value = false
                    return@withLock null
                }
                val restaurantId = sessionManager.getRestaurantId()
                if (restaurantId == 0L) {
                    _error.value = "Account not set up. Please log out and log in again."
                    _isLoading.value = false
                    return@withLock null
                }

                val terminalIdentity = requireActiveTerminalIdentity() ?: run {
                    _isLoading.value = false
                    return@withLock null
                }

                val finalSummary = cartManager.computeSummary(cartManager.currentItems, profile)
                cartManager.setSummary(finalSummary)
if (!validatePaymentLimits(finalSummary.total, paymentStateManager.paymentMode.value, paymentStateManager.partAmount1.value, paymentStateManager.partAmount2.value)) {
                    _isLoading.value = false
                    return@withLock null
                }
                // UPI QR generation and payment capture must work offline. Reserve the bill
                // number locally, then let background sync reconcile with the server later.
                val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
                val today = java.time.LocalDate.now(zoneId).toString()
                val dailyCounter = restaurantRepository.incrementAndGetTerminalDailyCounter(
                    terminalIdentity.terminalId,
                    terminalIdentity.terminalSeries,
                    today
                )
                val terminalSeries = terminalIdentity.terminalSeries
                val displayId = OrderIdManager.getDailyOrderDisplay(today, dailyCounter, terminalSeries)
                val createdAt = System.currentTimeMillis()
                val invoice = allocateInvoiceIdentity(createdAt)
                val publicToken = UUID.randomUUID().toString()
                val operationId = "${restaurantId}:${terminalIdentity.terminalId}:$publicToken:create_bill"

                val bill = BillEntity(
                    restaurantId = restaurantId,
                    deviceId = terminalIdentity.deviceId,
                    terminalId = terminalIdentity.terminalId,
                    createdTerminalId = terminalIdentity.terminalId,
                    createdDeviceId = terminalIdentity.deviceId,
                    currentOwnerTerminalId = terminalIdentity.terminalId,
                    dailyOrderId = dailyCounter,
                    dailyOrderDisplay = displayId,
                    lifetimeOrderId = null,
                    terminalSeries = terminalSeries,
                    financialYear = invoice?.financialYear,
                    invoiceSeries = invoice?.invoiceSeries,
                    invoiceSequence = invoice?.invoiceSequence,
                    invoiceNumber = invoice?.invoiceNumber,
                    orderType = _orderType.value,
                    customerName = _customerName.value.ifBlank { null },
                    customerWhatsapp = _customerWhatsapp.value.ifBlank { null },
                    subtotal = finalSummary.subtotal,
                    gstPercentage = profile.gstPercentage.toString(),
                    cgstAmount = finalSummary.cgst,
                    sgstAmount = finalSummary.sgst,
                    customTaxAmount = finalSummary.customTax,
                    totalAmount = finalSummary.total,
                    paymentMode = paymentStateManager.paymentMode.value.dbValue,
                    partAmount1 = paymentStateManager.partAmount1.value,
                    partAmount2 = paymentStateManager.partAmount2.value,
                    paymentStatus = PaymentStatus.PENDING.dbValue,
                    orderStatus = OrderStatus.DRAFT.dbValue,
                    cancelReason = "",
                    createdBy = sessionManager.getActiveUserId(),
                    createdAt = createdAt,
                    paidAt = null,
                    lastResetDate = today,
                    publicToken = publicToken,
                    ownerUserId = sessionManager.getActiveUserId(),
                    ownerRestaurantId = sessionManager.getRestaurantId(),
                    operationId = operationId
                )

                val items = cartManager.currentItems.map { cartItem ->
                    val price = cartItem.variant?.price ?: cartItem.item.basePrice
                    val itemTotal = (java.math.BigDecimal(price)
                        .multiply(java.math.BigDecimal.valueOf(cartItem.quantity.toLong())))
                        .setScale(2, java.math.RoundingMode.HALF_UP).toString()
                    BillItemEntity(
                        billId = 0,
                        menuItemId = cartItem.item.id,
                        itemName = cartItem.item.name,
                        variantId = cartItem.variant?.id,
                        variantName = cartItem.variant?.variantName,
                        price = price,
                        quantity = cartItem.quantity,
                        itemTotal = itemTotal,
                        specialInstruction = cartItem.note
                    )
                }

                val insertedBillId = billRepository.insertFullBill(bill, items, emptyList(), false)
                val inserted = billRepository.getBillWithItemsById(insertedBillId)
                _lastBill.value = inserted

                val draftBillId = inserted?.bill?.id ?: run {
                    _error.value = "Failed to retrieve draft bill. Please try again."
                    _isLoading.value = false
                    return@withLock null
                }
                savedStateHandle[PENDING_ONLINE_BILL_ID] = draftBillId

                invalidateRestoration()
                _isLoading.value = false
                syncManager.triggerImmediateSync()
                draftBillId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create draft bill", e)
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Unable to start online payment. Please try again."
                )
                _isLoading.value = false
                null
            }
        }
    }

    suspend fun finalizeOnlineBill(localBillId: Long, status: PaymentStatus, cancelReason: String = ""): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            _isLoading.value = true
            try {
                val billWithItems = billRepository.getBillWithItemsById(localBillId)
                val bill = billWithItems?.bill ?: run {
                    _error.value = "Bill not found."
                    _isLoading.value = false
                    return@withLock false
                }
                if (status != PaymentStatus.SUCCESS) {
                    if (bill.orderStatus == OrderStatus.DRAFT.dbValue &&
                        bill.paymentStatus == PaymentStatus.PENDING.dbValue
                    ) {
                        billRepository.cancelOrder(localBillId, cancelReason, false)
                    }
                    clearGatewayResult()
                    savedStateHandle[PENDING_ONLINE_BILL_ID] = null
                    _lastBill.value = billRepository.getBillWithItemsById(localBillId)
                    syncManager.triggerImmediateSync()
                    _isLoading.value = false
                    return@withLock true
                }
                if (!validatePaymentLimits(bill.totalAmount, paymentStateManager.paymentMode.value, paymentStateManager.partAmount1.value, paymentStateManager.partAmount2.value)) {
                    _isLoading.value = false
                    return@withLock false
                }

                val paymentOperationBase = bill.operationId
                    ?: "${bill.restaurantId}:${bill.terminalId}:${bill.publicToken}:finalize"
                val payments = paymentStateManager.buildPaymentEntities(
                    billId = localBillId,
                    paymentMode = paymentStateManager.paymentMode.value,
                    totalAmount = bill.totalAmount,
                    partAmount1 = paymentStateManager.partAmount1.value,
                    partAmount2 = paymentStateManager.partAmount2.value,
                    operationBase = paymentOperationBase
                )
                val finalized = billRepository.finalizeOnlineBill(
                    billId = localBillId,
                    payments = payments,
                    completedAt = System.currentTimeMillis()
                )
                _lastBill.value = finalized.billWithItems
                cartManager.clear()
                invalidateRestoration()
                clearGatewayResult()
                savedStateHandle[PENDING_ONLINE_BILL_ID] = null
                if (finalized.outcome ==
                    com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome.FINALIZED_NOW
                ) {
                    syncManager.triggerImmediateSync()
                }

                _isLoading.value = false
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize online bill", e)
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Unable to finalize payment result. Please sync again."
                )
                _isLoading.value = false
                false
            }
        }
    }

    suspend fun completeOrder(status: PaymentStatus, cancelReason: String = ""): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            if (!permissionManager.hasPermission(com.khanabook.lite.pos.domain.manager.PermissionManager.BILLING_CREATE)) {
                _error.value = "You don't have permission to create bills. Request access from your owner."
                return@withLock false
            }
            if (cartManager.currentItems.isEmpty()) {
                _error.value = "Add at least one item before completing the bill."
                return@withLock false
            }

            val unavailableItems = cartManager.currentItems.filter { cartItem ->
                val latest = menuRepository.getItemById(cartItem.item.id)
                latest == null || !latest.isAvailable
            }
            if (unavailableItems.isNotEmpty()) {
                val names = unavailableItems.joinToString(", ") { it.item.name }
                _error.value = "These items are now unavailable: $names. Please remove them to continue."
                return@withLock false
            }

            _isLoading.value = true
            try {
                // Use cached profile — no extra DB read needed
                val profile = _cachedProfile.value ?: restaurantRepository.getProfile()
                if (profile == null) {
                    _isLoading.value = false
                    return@withLock false
                }

                val restaurantId = sessionManager.getRestaurantId()
                if (restaurantId == 0L) {
                    _error.value = "Account not set up. Please log out and log in again."
                    _isLoading.value = false
                    return@withLock false
                }

                val terminalIdentity = requireActiveTerminalIdentity() ?: run {
                    _isLoading.value = false
                    return@withLock false
                }

                val finalSummary = cartManager.computeSummary(cartManager.currentItems, profile)
                cartManager.setSummary(finalSummary)
                if (!validatePaymentLimits(finalSummary.total, paymentStateManager.paymentMode.value, paymentStateManager.partAmount1.value, paymentStateManager.partAmount2.value)) {
                    _isLoading.value = false
                    return@withLock false
                }

                val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
                val today = java.time.LocalDate.now(zoneId).toString()
                val dailyCounter = restaurantRepository.incrementAndGetTerminalDailyCounter(
                    terminalIdentity.terminalId,
                    terminalIdentity.terminalSeries,
                    today
                )
                val terminalSeries = terminalIdentity.terminalSeries
                val displayId = OrderIdManager.getDailyOrderDisplay(today, dailyCounter, terminalSeries)
                val createdAt = System.currentTimeMillis()
                val invoice = allocateInvoiceIdentity(createdAt)
                val publicToken = UUID.randomUUID().toString()
                val operationId = "$restaurantId:${terminalIdentity.terminalId}:$publicToken:create_bill"

                val bill = BillEntity(
                    restaurantId = sessionManager.getRestaurantId(),
                    deviceId = terminalIdentity.deviceId,
                    terminalId = terminalIdentity.terminalId,
                    createdTerminalId = terminalIdentity.terminalId,
                    createdDeviceId = terminalIdentity.deviceId,
                    currentOwnerTerminalId = terminalIdentity.terminalId,
                    dailyOrderId = dailyCounter,
                    dailyOrderDisplay = displayId,
                    lifetimeOrderId = null,
                    terminalSeries = terminalSeries,
                    financialYear = invoice?.financialYear,
                    invoiceSeries = invoice?.invoiceSeries,
                    invoiceSequence = invoice?.invoiceSequence,
                    invoiceNumber = invoice?.invoiceNumber,
                    orderType = _orderType.value,
                    customerName = _customerName.value.ifBlank { null },
                    customerWhatsapp = _customerWhatsapp.value.ifBlank { null },
                    subtotal = finalSummary.subtotal,
                    gstPercentage = profile.gstPercentage.toString(),
                    cgstAmount = finalSummary.cgst,
                    sgstAmount = finalSummary.sgst,
                    customTaxAmount = finalSummary.customTax,
                    totalAmount = finalSummary.total,
                    paymentMode = paymentStateManager.paymentMode.value.dbValue,
                    partAmount1 = paymentStateManager.partAmount1.value,
                    partAmount2 = paymentStateManager.partAmount2.value,
                    paymentStatus = status.dbValue,
                    orderStatus = if (status == PaymentStatus.SUCCESS) OrderStatus.COMPLETED.dbValue else OrderStatus.CANCELLED.dbValue,
                    cancelReason = if (status == PaymentStatus.FAILED) cancelReason else "",
                    createdBy = sessionManager.getActiveUserId(),
                    createdAt = createdAt,
                    paidAt = if (status == PaymentStatus.SUCCESS) System.currentTimeMillis() else null,
                    lastResetDate = today,
                    publicToken = publicToken,
                    ownerUserId = sessionManager.getActiveUserId(),
                    ownerRestaurantId = sessionManager.getRestaurantId(),
                    operationId = operationId
                )

                val items = cartManager.currentItems.map { cartItem ->
                    val price = cartItem.variant?.price ?: cartItem.item.basePrice
                    val itemTotal = (java.math.BigDecimal(price)
                        .multiply(java.math.BigDecimal.valueOf(cartItem.quantity.toLong())))
                        .setScale(2, java.math.RoundingMode.HALF_UP).toString()
                    BillItemEntity(
                        billId = 0,
                        menuItemId = cartItem.item.id,
                        itemName = cartItem.item.name,
                        variantId = cartItem.variant?.id,
                        variantName = cartItem.variant?.variantName,
                        price = price,
                        quantity = cartItem.quantity,
                        itemTotal = itemTotal,
                        specialInstruction = cartItem.note
                    )
                }

                val payments = paymentStateManager.buildPaymentEntities(
                    billId = 0,
                    paymentMode = paymentStateManager.paymentMode.value,
                    totalAmount = finalSummary.total,
                    partAmount1 = paymentStateManager.partAmount1.value,
                    partAmount2 = paymentStateManager.partAmount2.value,
                    operationBase = operationId
                )

                val insertedBillId = billRepository.insertFullBill(
                    bill,
                    items,
                    payments.filter(::shouldPersistLocally),
                    false
                )
                val inserted = billRepository.getBillWithItemsById(insertedBillId)
                _lastBill.value = inserted
                printCoordinator.clearStatus()
                syncManager.triggerImmediateSync()

                // Launch auto-print asynchronously — never blocks bill completion
                // Launch auto-print via PrintService (Foreground Service) to handle background lifecycle safely
                if (inserted != null && status == PaymentStatus.SUCCESS) {
                    try {
                        com.khanabook.lite.pos.domain.manager.PrintService.startPrintJob(
                            context = appContext,
                            billId = inserted.bill.id,
                            mode = PrintDispatchMode.AUTO
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start PrintService for auto-print", e)
                    }
                }

                // Clearing the cart automatically triggers the combine{} → new BillSummary(empty)
                // No need to call updateSummary() manually.
                invalidateRestoration()
                cartManager.clear()
                _error.value = null
                _isLoading.value = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save bill", e)
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to save bill. Please try again."
                )
                _isLoading.value = false
                false
            }
        }
    }

    fun loadDraftOrderForEditing(billId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Explicitly loading a different bill supersedes any in-flight restoration,
            // so a stale restoration result can neither overwrite nor clear this session.
            invalidateRestoration()
            _isLoading.value = true
            try {
                val billWithItems = billRepository.getBillWithItemsById(billId)
                if (billWithItems != null) {
                    if (billWithItems.bill.orderStatus == OrderStatus.COMPLETED.dbValue || 
                        billWithItems.bill.paymentStatus == PaymentStatus.SUCCESS.dbValue) {
                        _error.value = "Cannot edit a settled order."
                        return@launch
                    }
                    if (billWithItems.bill.orderStatus == OrderStatus.CANCELLED.dbValue) {
                        _error.value = "Cannot edit a cancelled order."
                        return@launch
                    }
                    editingBillId = billId
                    _customerName.value = billWithItems.bill.customerName ?: ""
                    _customerWhatsapp.value = billWithItems.bill.customerWhatsapp ?: ""
                    _orderType.value = billWithItems.bill.orderType ?: "dine_in"

                    val cartList = billWithItems.items.map { billItem ->
                        val menuItem = menuRepository.getItemById(billItem.menuItemId ?: 0L)
                            ?: billItem.toRestorableMenuItem()
                        val variant = billItem.variantId?.let { menuRepository.getVariantById(it) }
                            ?: billItem.toRestorableVariant(menuItem.id)
                        CartItem(
                            item = menuItem,
                            variant = variant,
                            quantity = billItem.quantity,
                            note = billItem.specialInstruction ?: ""
                        )
                    }.groupBy { (it.item.id) to it.variant?.id }
                        .map { (_, groupItems) ->
                            val first = groupItems.first()
                            val totalQty = groupItems.sumOf { it.quantity }
                            first.copy(quantity = totalQty)
                        }
                    cartManager.setItems(cartList)
                    cartManager.setSummary(BillSummary(
                        subtotal = billWithItems.bill.subtotal,
                        cgst = billWithItems.bill.cgstAmount,
                        sgst = billWithItems.bill.sgstAmount,
                        customTax = billWithItems.bill.customTaxAmount,
                        total = billWithItems.bill.totalAmount
                    ))
                    paymentStateManager.setPersistedPaymentTotal(billWithItems.bill.totalAmount)
                    paymentStateManager.setPaymentRecovery(
                        billRepository.getPaymentRecoveryAssessment(billId))
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load draft order", e)
                _error.value = "Failed to load order: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearActiveSession() {
        invalidateRestoration()
        editingBillId = null
        cartManager.clear()
        _customerName.value = ""
        _customerWhatsapp.value = ""
        _orderType.value = "dine_in"
        paymentStateManager.setPersistedPaymentTotal(null)
        paymentStateManager.setPaymentRecovery(PaymentRecoveryAssessment.Empty)
    }

    suspend fun saveDraftOrder(tableName: String): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            if (cartManager.currentItems.isEmpty()) {
                _error.value = "Add at least one item before saving the draft."
                return@withLock false
            }
            _isLoading.value = true
            try {
                val profile = _cachedProfile.value ?: restaurantRepository.getProfile()
                if (profile == null) {
                    _isLoading.value = false
                    return@withLock false
                }
                val restaurantId = sessionManager.getRestaurantId()
                if (restaurantId == 0L) {
                    _error.value = "Account not set up. Please log out and log in again."
                    _isLoading.value = false
                    return@withLock false
                }

                val terminalIdentity = requireActiveTerminalIdentity() ?: run {
                    _isLoading.value = false
                    return@withLock false
                }

                val finalSummary = cartManager.computeSummary(cartManager.currentItems, profile)
                cartManager.setSummary(finalSummary)

                val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
                val today = java.time.LocalDate.now(zoneId).toString()
                val dailyCounter = restaurantRepository.incrementAndGetTerminalDailyCounter(
                    terminalIdentity.terminalId,
                    terminalIdentity.terminalSeries,
                    today
                )
                val terminalSeries = terminalIdentity.terminalSeries
                val displayId = OrderIdManager.getDailyOrderDisplay(today, dailyCounter, terminalSeries)
                val createdAt = System.currentTimeMillis()
                val invoice = allocateInvoiceIdentity(createdAt)
                val publicToken = UUID.randomUUID().toString()
                val operationId = "$restaurantId:${terminalIdentity.terminalId}:$publicToken:create_draft"

                val bill = BillEntity(
                    restaurantId = restaurantId,
                    deviceId = terminalIdentity.deviceId,
                    terminalId = terminalIdentity.terminalId,
                    createdTerminalId = terminalIdentity.terminalId,
                    createdDeviceId = terminalIdentity.deviceId,
                    currentOwnerTerminalId = terminalIdentity.terminalId,
                    dailyOrderId = dailyCounter,
                    dailyOrderDisplay = displayId,
                    lifetimeOrderId = null,
                    terminalSeries = terminalSeries,
                    financialYear = invoice?.financialYear,
                    invoiceSeries = invoice?.invoiceSeries,
                    invoiceSequence = invoice?.invoiceSequence,
                    invoiceNumber = invoice?.invoiceNumber,
                    orderType = "dine_in",
                    customerName = tableName.ifBlank { "Table" },
                    customerWhatsapp = _customerWhatsapp.value.ifBlank { null },
                    subtotal = finalSummary.subtotal,
                    gstPercentage = profile.gstPercentage.toString(),
                    cgstAmount = finalSummary.cgst,
                    sgstAmount = finalSummary.sgst,
                    customTaxAmount = finalSummary.customTax,
                    totalAmount = finalSummary.total,
                    paymentMode = PaymentMode.CASH.dbValue,
                    paymentStatus = PaymentStatus.PENDING.dbValue,
                    orderStatus = OrderStatus.DRAFT.dbValue,
                    createdBy = sessionManager.getActiveUserId(),
                    createdAt = createdAt,
                    paidAt = null,
                    lastResetDate = today,
                    publicToken = publicToken,
                    ownerUserId = sessionManager.getActiveUserId(),
                    ownerRestaurantId = sessionManager.getRestaurantId(),
                    operationId = operationId
                )

                val items = cartManager.currentItems.map { cartItem ->
                    val price = cartItem.variant?.price ?: cartItem.item.basePrice
                    val itemTotal = (java.math.BigDecimal(price)
                        .multiply(java.math.BigDecimal.valueOf(cartItem.quantity.toLong())))
                        .setScale(2, java.math.RoundingMode.HALF_UP).toString()
                    BillItemEntity(
                        billId = 0,
                        menuItemId = cartItem.item.id,
                        itemName = cartItem.item.name,
                        variantId = cartItem.variant?.id,
                        variantName = cartItem.variant?.variantName,
                        price = price,
                        quantity = cartItem.quantity,
                        itemTotal = itemTotal,
                        specialInstruction = cartItem.note,
                        sentToKot = false
                    )
                }

                val insertedBillId = billRepository.insertFullBill(bill, items, emptyList(), false)
                val inserted = billRepository.getBillWithItemsById(insertedBillId)
                _lastBill.value = inserted
                printCoordinator.clearStatus()

                if (inserted != null) {
                    try {
                        com.khanabook.lite.pos.domain.manager.PrintService.startPrintJob(
                            context = appContext,
                            billId = inserted.bill.id,
                            mode = PrintDispatchMode.AUTO
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start PrintService for draft KOT print", e)
                    }
                }

                invalidateRestoration()
                cartManager.clear()
                _customerName.value = ""
                _customerWhatsapp.value = ""
                syncManager.triggerImmediateSync()
                _isLoading.value = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save draft order", e)
                _error.value = e.message ?: "Failed to save draft order"
                _isLoading.value = false
                false
            }
        }
    }

    suspend fun appendItemsToDraft(billId: Long): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            if (cartManager.currentItems.isEmpty()) {
                _error.value = "Cart is empty."
                return@withLock false
            }
            _isLoading.value = true
            try {
                val profile = _cachedProfile.value ?: restaurantRepository.getProfile()
                if (profile == null) {
                    _isLoading.value = false
                    return@withLock false
                }
                val existingWithItems = billRepository.getBillWithItemsById(billId)
                if (existingWithItems == null) {
                    _isLoading.value = false
                    return@withLock false
                }
                val existingBill = existingWithItems.bill
                if (existingBill.orderStatus == OrderStatus.COMPLETED.dbValue || 
                    existingBill.paymentStatus == PaymentStatus.SUCCESS.dbValue) {
                    _error.value = "Cannot update a settled order."
                    _isLoading.value = false
                    return@withLock false
                }
                if (existingBill.orderStatus == OrderStatus.CANCELLED.dbValue) {
                    _error.value = "Cannot update a cancelled order."
                    _isLoading.value = false
                    return@withLock false
                }

                val restaurantId = sessionManager.getRestaurantId()
                if (restaurantId == 0L) {
                    _error.value = "Account not set up."
                    _isLoading.value = false
                    return@withLock false
                }

                val existingItems = existingWithItems.items
                val cartItems = cartManager.currentItems

                val dbTotals = existingItems.groupBy { (it.menuItemId ?: 0L) to it.variantId }
                val processedDbKeys = mutableSetOf<Pair<Long, Long?>>()

                for (cartItem in cartItems) {
                    val key = cartItem.item.id to cartItem.variant?.id
                    processedDbKeys.add(key)

                    val dbRows = dbTotals[key] ?: emptyList()
                    val totalDbQty = dbRows.sumOf { it.quantity }

                    if (totalDbQty == 0) {
                        val price = cartItem.variant?.price ?: cartItem.item.basePrice
                        val itemTotal = (java.math.BigDecimal(price)
                            .multiply(java.math.BigDecimal.valueOf(cartItem.quantity.toLong())))
                            .setScale(2, java.math.RoundingMode.HALF_UP).toString()

                        val newItem = BillItemEntity(
                            billId = billId,
                            menuItemId = cartItem.item.id,
                            itemName = cartItem.item.name,
                            variantId = cartItem.variant?.id,
                            variantName = cartItem.variant?.variantName,
                            price = price,
                            quantity = cartItem.quantity,
                            itemTotal = itemTotal,
                            specialInstruction = cartItem.note,
                            sentToKot = false,
                            restaurantId = restaurantId,
                            deviceId = sessionManager.getDeviceId(),
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        billRepository.insertBillItems(listOf(newItem))
                    } else if (cartItem.quantity > totalDbQty) {
                        val diffQty = cartItem.quantity - totalDbQty
                        val price = cartItem.variant?.price ?: cartItem.item.basePrice
                        val itemTotal = (java.math.BigDecimal(price)
                            .multiply(java.math.BigDecimal.valueOf(diffQty.toLong())))
                            .setScale(2, java.math.RoundingMode.HALF_UP).toString()

                        val newItem = BillItemEntity(
                            billId = billId,
                            menuItemId = cartItem.item.id,
                            itemName = cartItem.item.name,
                            variantId = cartItem.variant?.id,
                            variantName = cartItem.variant?.variantName,
                            price = price,
                            quantity = diffQty,
                            itemTotal = itemTotal,
                            specialInstruction = cartItem.note,
                            sentToKot = false,
                            restaurantId = restaurantId,
                            deviceId = sessionManager.getDeviceId(),
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        billRepository.insertBillItems(listOf(newItem))
                    } else if (cartItem.quantity < totalDbQty) {
                        var reductionNeeded = totalDbQty - cartItem.quantity
                        val sortedDbRows = dbRows.sortedWith(compareBy<BillItemEntity> { it.sentToKot }.thenByDescending { it.id })

                        for (row in sortedDbRows) {
                            if (reductionNeeded <= 0) break
                            if (row.quantity <= reductionNeeded) {
                                reductionNeeded -= row.quantity
                                billRepository.deleteBillItemById(row.id)
                            } else {
                                val newQty = row.quantity - reductionNeeded
                                reductionNeeded = 0
                                val price = row.price
                                val newItemTotal = (java.math.BigDecimal(price)
                                    .multiply(java.math.BigDecimal.valueOf(newQty.toLong())))
                                    .setScale(2, java.math.RoundingMode.HALF_UP).toString()

                                billRepository.updateBillItem(row.copy(
                                    quantity = newQty,
                                    itemTotal = newItemTotal,
                                    isSynced = false,
                                    updatedAt = System.currentTimeMillis()
                                ))
                            }
                        }
                    }
                }

                for (dbKey in dbTotals.keys) {
                    if (dbKey !in processedDbKeys) {
                        val dbRows = dbTotals[dbKey] ?: emptyList()
                        for (row in dbRows) {
                            billRepository.deleteBillItemById(row.id)
                        }
                    }
                }

                val allItems = billRepository.getBillWithItemsById(billId)?.items ?: emptyList()
                val subtotal = BillCalculator.calculateSubtotal(allItems.map {
                    it.price to it.quantity
                })

                var cgst = "0.0"
                var sgst = "0.0"
                var customTax = "0.0"

                if (profile.gstEnabled) {
                    val gst = BillCalculator.calculateGST(subtotal, profile.gstPercentage)
                    cgst = gst.cgst
                    sgst = gst.sgst
                } else if (profile.customTaxPercentage > 0) {
                    customTax = BillCalculator.calculateCustomTax(subtotal, profile.customTaxPercentage)
                }

                val total = BillCalculator.calculateTotal(subtotal, cgst, sgst, customTax)

                val updatedBill = existingBill.copy(
                    subtotal = subtotal,
                    cgstAmount = cgst,
                    sgstAmount = sgst,
                    customTaxAmount = customTax,
                    totalAmount = total,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )

                billRepository.updateBill(updatedBill, false)

                val inserted = billRepository.getBillWithItemsById(billId)
                _lastBill.value = inserted
                printCoordinator.clearStatus()

                if (inserted != null) {
                    try {
                        com.khanabook.lite.pos.domain.manager.PrintService.startPrintJob(
                            context = appContext,
                            billId = billId,
                            mode = PrintDispatchMode.AUTO
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start PrintService for incremental KOT", e)
                    }
                }

                invalidateRestoration()
                cartManager.clear()
                syncManager.triggerImmediateSync()
                _isLoading.value = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append items to draft", e)
                _error.value = e.message ?: "Failed to append items"
                _isLoading.value = false
                false
            }
        }
    }

    suspend fun settleDraftOrder(
        billId: Long,
        paymentMode: PaymentMode,
        status: PaymentStatus,
        partAmount1: String = "0.0",
        partAmount2: String = "0.0"
    ): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            _isLoading.value = true
            try {
                val existingWithItems = billRepository.getBillWithItemsById(billId)
                if (existingWithItems == null) {
                    _isLoading.value = false
                    return@withLock false
                }
                val existingBill = existingWithItems.bill
                if (existingBill.orderStatus == OrderStatus.COMPLETED.dbValue || 
                    existingBill.paymentStatus == PaymentStatus.SUCCESS.dbValue
                ) {
                    _error.value = "Order is already settled."
                    _isLoading.value = false
                    return@withLock false
                }
                if (existingBill.orderStatus == OrderStatus.CANCELLED.dbValue) {
                    _error.value = "Cannot settle a cancelled order."
                    _isLoading.value = false
                    return@withLock false
                }

                if (status == PaymentStatus.FAILED) {
                    billRepository.cancelOrder(billId, "Payment failed/cancelled", true)
                    invalidateRestoration()
                    _lastBill.value = billRepository.getBillWithItemsById(billId)
                    printCoordinator.clearStatus()
                    _isLoading.value = false
                    return@withLock true
                }

                if (!validatePaymentLimits(
                        existingBill.totalAmount,
                        paymentMode,
                        partAmount1,
                        partAmount2
                    )
                ) {
                    _isLoading.value = false
                    return@withLock false
                }

                // Route through validated finalization path (same as finalizeOnlineBill).
                // This checks: terminal ownership, existing payment rows (rejects malformed,
                // duplicate, partial sets), PaymentSetValidator validation, idempotent retry,
                // inventory consumption boundary.
                val paymentOperationBase = existingBill.operationId
                    ?: "${existingBill.restaurantId}:${existingBill.terminalId}:${existingBill.publicToken}:finalize"
                val payments = paymentStateManager.buildPaymentEntities(
                    billId = billId,
                    paymentMode = paymentMode,
                    totalAmount = existingBill.totalAmount,
                    partAmount1 = partAmount1,
                    partAmount2 = partAmount2,
                    operationBase = paymentOperationBase,
                    deviceId = sessionManager.getDeviceId(),
                    restaurantId = sessionManager.getRestaurantId()
                )

                val finalized = billRepository.finalizeOnlineBill(
                    billId = billId,
                    payments = payments,
                    completedAt = System.currentTimeMillis()
                )

                invalidateRestoration()
                _lastBill.value = finalized.billWithItems
                printCoordinator.clearStatus()

                if (finalized.outcome ==
                    com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome.FINALIZED_NOW
                ) {
                    syncManager.triggerImmediateSync()
                }

                // Auto-print on successful settlement
                try {
                    com.khanabook.lite.pos.domain.manager.PrintService.startPrintJob(
                        context = appContext,
                        billId = billId,
                        mode = PrintDispatchMode.AUTO
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start PrintService for settled draft receipt", e)
                }

                _isLoading.value = false
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (e.message?.contains("malformed", ignoreCase = true) == true ||
                    e.message?.contains("conflicting payment set", ignoreCase = true) == true ||
                    e.message?.contains("Duplicate", ignoreCase = true) == true
                ) {
                    "This order has incomplete payment records and cannot be completed automatically. Review or contact support."
                } else {
                    UserMessageSanitizer.sanitize(e, "Failed to settle draft order")
                }
                Log.e(TAG, "Failed to settle draft order", e)
                _error.value = message
                _isLoading.value = false
                false
            }
        }
    }

    suspend fun finalizeRecoveredPaymentSet(billId: Long): Boolean =
        finalizePaymentRecovery(billId) {
            billRepository.finalizeExistingPaymentSet(
                billId = billId,
                completedAt = System.currentTimeMillis()
            )
        }

    suspend fun recoverPartialDraftPayment(
        billId: Long,
        paymentMode: PaymentMode
    ): Boolean {
        if (paymentMode !in setOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.POS)) {
            _error.value = "Choose one payment mode for the remaining amount."
            return false
        }
        return finalizePaymentRecovery(billId) {
            billRepository.recoverPartialPayment(
                billId = billId,
                paymentMode = paymentMode.dbValue,
                completedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun finalizePaymentRecovery(
        billId: Long,
        finalize: suspend () -> com.khanabook.lite.pos.data.local.relation.BillFinalizationResult
    ): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            _isLoading.value = true
            try {
                val finalized = finalize()
                invalidateRestoration()
                _lastBill.value = finalized.billWithItems
                cartManager.clear()
                paymentStateManager.setPersistedPaymentTotal(null)
                paymentStateManager.setPaymentRecovery(PaymentRecoveryAssessment.Empty)
                editingBillId = null
                printCoordinator.clearStatus()
                if (finalized.outcome ==
                    com.khanabook.lite.pos.data.local.relation.BillFinalizationOutcome.FINALIZED_NOW
                ) {
                    syncManager.triggerImmediateSync()
                }
                try {
                    com.khanabook.lite.pos.domain.manager.PrintService.startPrintJob(
                        context = appContext,
                        billId = billId,
                        mode = PrintDispatchMode.AUTO
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start PrintService after payment recovery", e)
                }
                _isLoading.value = false
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recover draft payment", e)
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Unable to recover this payment safely."
                )
                _isLoading.value = false
                false
            }
        }
    }

    suspend fun resetPaymentRecovery(billId: Long): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            _isLoading.value = true
            try {
                val recovery = billRepository.resetUnverifiedPaymentRecovery(billId)
                paymentStateManager.setPaymentRecovery(recovery)
                if (recovery is PaymentRecoveryAssessment.Empty) {
                    paymentStateManager.setPartAmounts("0.0", "0.0")
                }
                syncManager.triggerImmediateSync()
                _isLoading.value = false
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset payment recovery", e)
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "This payment attempt cannot be reset on this device."
                )
                _isLoading.value = false
                false
            }
        }
    }

    private fun shouldPersistLocally(payment: BillPaymentEntity): Boolean {
        return true
    }

    fun updateCartItemNote(item: MenuItemEntity, variant: ItemVariantEntity?, note: String) {
        cartManager.updateItemNote(item, variant, note)
    }

    fun clearError() {
        _error.value = null
    }

    fun clearPrintStatus() {
        printCoordinator.clearStatus()
    }

    fun reportError(message: String) {
        _error.value = message
    }

    suspend fun prepareLastBillForInvoiceShare(): BillWithItems? {
        val current = _lastBill.value ?: return null
        if (current.bill.serverId != null && current.bill.publicToken != null) return current

        return withContext(Dispatchers.IO) {
            val refreshed = billRepository.getBillWithItemsById(current.bill.id)

            if (refreshed != null) {
                withContext(Dispatchers.Main) {
                    _lastBill.value = refreshed
                }
            }

            refreshed
        }
    }

    fun printReceipt(bill: BillWithItems) {
        val profile = _cachedProfile.value
        viewModelScope.launch(Dispatchers.IO) {
            printCoordinator.printReceipt(bill, profile) { errorMsg ->
                _error.value = errorMsg
            }
        }
    }

    fun printKitchenTicket(bill: BillWithItems) {
        val profile = _cachedProfile.value
        viewModelScope.launch(Dispatchers.IO) {
            printCoordinator.printKitchenTicket(bill, profile) { errorMsg ->
                _error.value = errorMsg
            }
        }
    }

    @Immutable
    data class CartItem(val item: MenuItemEntity, val variant: ItemVariantEntity? = null, val quantity: Int, val note: String = "")
    
    @Immutable
    data class BillSummary(val subtotal: String = "0.0", val cgst: String = "0.0", val sgst: String = "0.0", val customTax: String = "0.0", val total: String = "0.0")

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            printerManager.disconnect()
        }
    }

    private fun describeSyncFailure(error: Throwable?): String {
        if (error == null) {
            return "Sync did not complete. Please check your connection and try again."
        }
        return when (error) {
            is retrofit2.HttpException -> when (error.code()) {
                401, 403 -> "Session expired. Please log out and log in again."
                409 -> "Sync conflict. Please retry in a moment."
                in 400..499 -> "Server rejected the bill data (HTTP ${error.code()}). Please try again or contact support."
                in 500..599 -> "Server error (HTTP ${error.code()}). Please try again shortly."
                else -> "Sync failed: HTTP ${error.code()}. ${error.message()}"
            }
            is com.khanabook.lite.pos.domain.util.SyncConflictException ->
                "Sync conflict. Please retry in a moment."
            is IllegalStateException -> {
                val msg = error.message ?: ""
                when {
                    msg.contains("Push phase aborted", ignoreCase = true) ->
                        "Account setup is incomplete on this device. Please log out and log in again to refresh your profile."
                    msg.contains("server timestamp", ignoreCase = true) ->
                        "Server returned an invalid response. Please try again or contact support."
                    else -> "Sync failed: $msg"
                }
            }
            is java.net.UnknownHostException, is java.net.ConnectException ->
                "Cannot reach server. Check your internet connection."
            is java.net.SocketTimeoutException ->
                "Server is slow to respond. Please try again."
            is java.io.IOException ->
                "Network error: ${error.message ?: "connection lost"}. Please try again."
            else -> "Sync failed: ${error.message ?: error.javaClass.simpleName}"
        }
    }
}
