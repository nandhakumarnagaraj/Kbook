package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.domain.util.AppConstants

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.repository.MenuRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.InvoicePDFGenerator
import com.khanabook.lite.pos.domain.manager.OrderIdManager
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import com.khanabook.lite.pos.domain.manager.PaymentRecoveryAssessment
import com.khanabook.lite.pos.domain.manager.PrintDispatchMode
import com.khanabook.lite.pos.domain.manager.PrintRouter
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.PaymentLimits
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
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
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

    val cachedProfile: StateFlow<RestaurantProfileEntity?> get() = _cachedProfile

    val connectionStatus: StateFlow<com.khanabook.lite.pos.domain.util.ConnectionStatus> =
        networkMonitor.status.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            com.khanabook.lite.pos.domain.util.ConnectionStatus.Unavailable
        )

    private val _gatewayTxnId = MutableStateFlow<String?>(null)
    private val _gatewayStatus = MutableStateFlow<String?>(null)

    fun setGatewayResult(txnId: String?, status: String?) {
        _gatewayTxnId.value = txnId
        _gatewayStatus.value = status
    }

    fun clearGatewayResult() {
        _gatewayTxnId.value = null
        _gatewayStatus.value = null
    }

    companion object {
        private const val TAG = "BillingViewModel"
        private const val PENDING_ONLINE_BILL_ID = "pending_online_bill_id"
    }

    private val orderMutex = Mutex()

    // Cache the restaurant profile reactively — avoids repeated DB reads in updateSummary
    // and completeOrder. Stays automatically fresh because it's backed by a Flow.
    private val _cachedProfile: StateFlow<RestaurantProfileEntity?> =
        restaurantRepository.getProfileFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val gson = com.google.gson.Gson()

    private val _cartItems = MutableStateFlow<List<CartItem>>(
        savedStateHandle.get<String>("cart_items")?.let { json ->
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<CartItem>>() {}.type
                gson.fromJson<List<CartItem>>(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore cart items", e)
                emptyList()
            }
        } ?: emptyList()
    )
    val cartItems: StateFlow<List<CartItem>> = _cartItems

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

    private val _paymentMode = MutableStateFlow(PaymentMode.UPI)
    val paymentMode: StateFlow<PaymentMode> = _paymentMode

    private val _partAmount1 = MutableStateFlow("0.0")
    private val _partAmount2 = MutableStateFlow("0.0")
    val partAmount1: StateFlow<String> = _partAmount1
    val partAmount2: StateFlow<String> = _partAmount2

    private val _billSummary = MutableStateFlow(BillSummary())
    val billSummary: StateFlow<BillSummary> = _billSummary

    private val _persistedPaymentTotal = MutableStateFlow<String?>(null)
    val persistedPaymentTotal: StateFlow<String?> = _persistedPaymentTotal

    private val _paymentRecovery =
        MutableStateFlow<PaymentRecoveryAssessment>(PaymentRecoveryAssessment.Empty)
    val paymentRecovery: StateFlow<PaymentRecoveryAssessment> = _paymentRecovery
    
    private val _lastBill = MutableStateFlow<BillWithItems?>(null)
    val lastBill: StateFlow<BillWithItems?> = _lastBill

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _printStatus = MutableStateFlow<String?>(null)
    val printStatus: StateFlow<String?> = _printStatus

    private val _receiptPrinting = MutableStateFlow(false)
    val receiptPrinting: StateFlow<Boolean> = _receiptPrinting

    private val _kitchenPrinting = MutableStateFlow(false)
    val kitchenPrinting: StateFlow<Boolean> = _kitchenPrinting
    private val kitchenPrintInFlight = AtomicBoolean(false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var editingBillId: Long? = null

    private val _orderType = MutableStateFlow("dine_in")
    val orderType: StateFlow<String> = _orderType

    fun setOrderType(type: String) {
        _orderType.value = type
    }

    val activeDraftBillsFlow: Flow<List<BillEntity>> = billRepository.getActiveDraftBillsFlow()

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
            _cartItems.collect { items ->
                try {
                    val json = gson.toJson(items)
                    savedStateHandle["cart_items"] = json
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save cart items", e)
                }
            }
        }

        combine(_cartItems, _cachedProfile) { items, profile ->
            computeSummary(items, profile)
        }
            .onEach { _billSummary.value = it }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            printRouter.printResults.collect { (billId, result) ->
                val lastBillId = _lastBill.value?.bill?.id
                if (lastBillId == billId) {
                    val kitchenQueued = result.kitchenQueued ||
                        kitchenPrintQueueRepository.hasPendingForBill(billId)
                    if (result.attempted == 0) {
                        withContext(Dispatchers.Main) {
                            _printStatus.value = if (kitchenQueued) {
                                when (result.kitchenQueueReason) {
                                    "not_configured" ->
                                        "Kitchen printer not configured. KDS queued."
                                    else ->
                                        "Kitchen printer offline. KDS queued."
                                }
                            } else {
                                "No auto-print target configured."
                            }
                        }
                    } else if (kitchenQueued && result.succeeded > 0) {
                        withContext(Dispatchers.Main) {
                            _printStatus.value = if (result.successTargets.contains(PrinterRole.KITCHEN.name)) {
                                "Printed Kitchen ticket"
                            } else {
                                "Printed customer receipt. KDS queued."
                            }
                        }
                    } else if (kitchenQueued) {
                        withContext(Dispatchers.Main) {
                            _printStatus.value = when (result.kitchenQueueReason) {
                                "not_configured" ->
                                    "Kitchen printer not configured. KDS queued."
                                else ->
                                    "Kitchen printer offline. KDS queued."
                            }
                        }
                    } else if (result.failures.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            _printStatus.value = when {
                                result.successTargets.contains(PrinterRole.KITCHEN.name) ->
                                    "Printed Kitchen ticket"
                                else -> buildPrintStatusMessage(
                                    prefix = "Printed",
                                    targets = result.successTargets
                                )
                            }
                        }
                    } else if (result.succeeded > 0) {
                        withContext(Dispatchers.Main) {
                            _printStatus.value = buildPartialPrintStatus(result)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _error.value = "Auto-print failed. Bill saved."
                            _printStatus.value = "Printing failed for all configured printers."
                        }
                    }
                }
            }
        }
    }

    fun addToCart(item: MenuItemEntity, variant: ItemVariantEntity? = null) {
        viewModelScope.launch {
            val latestItem = menuRepository.getItemById(item.id) ?: item
            _cartItems.update { current ->
                val mutable = current.toMutableList()
                val existingInUpdate = mutable.find { it.item.id == item.id && it.variant?.id == variant?.id }

                if (existingInUpdate != null) {
                    val idx = mutable.indexOf(existingInUpdate)
                    mutable[idx] = existingInUpdate.copy(quantity = existingInUpdate.quantity + 1)
                } else {
                    mutable.add(CartItem(latestItem, variant, 1))
                }
                mutable
            }
        }
    }

    fun removeFromCart(item: MenuItemEntity, variant: ItemVariantEntity? = null) {
        _cartItems.update { current ->
            val mutable = current.toMutableList()
            val existing = mutable.find { it.item.id == item.id && it.variant?.id == variant?.id }
            if (existing != null) {
                val index = mutable.indexOf(existing)
                if (existing.quantity > 1) {
                    mutable[index] = existing.copy(quantity = existing.quantity - 1)
                } else {
                    mutable.removeAt(index)
                }
            }
            mutable
        }
    }

    
    fun handleScannedBarcode(barcode: String) {
        viewModelScope.launch {
            val menuItem = menuRepository.getMenuItemByCode(barcode)
            if (menuItem != null) {
                addToCart(menuItem)
            } else {
                _error.value = "No item found for barcode: $barcode"
            }
        }
    }

    
    fun addItemByScannedText(text: String) {
        viewModelScope.launch {
            val allItems = menuRepository.getAllMenuItemsOnce()
            val allVariants = menuRepository.getAllVariantsOnce()
            
            
            val lines = text.split("\n", "\r").map { it.trim() }.filter { it.length > 2 }
            
            for (line in lines) {
                
                val itemMatch = allItems.find { it.name.equals(line, ignoreCase = true) }
                if (itemMatch != null) {
                    addToCart(itemMatch)
                    continue
                }
                
                
                val variantMatch = allVariants.find { it.variantName.equals(line, ignoreCase = true) }
                if (variantMatch != null) {
                    val parentItem = allItems.find { it.id == variantMatch.menuItemId }
                    if (parentItem != null) {
                        addToCart(parentItem, variantMatch)
                        continue
                    }
                }
                
                
                val partialItem = allItems.find { line.contains(it.name, ignoreCase = true) }
                if (partialItem != null) {
                    
                    val partialVariant = allVariants.filter { it.menuItemId == partialItem.id }
                        .find { line.contains(it.variantName, ignoreCase = true) }
                    
                    addToCart(partialItem, partialVariant)
                }
            }
        }
    }

    /**
     * Pure function — computes a BillSummary from a list of cart items and a profile snapshot.
     * Called from the init combine{} block; no DB access, no side-effects.
     */
    private fun computeSummary(items: List<CartItem>, profile: RestaurantProfileEntity?): BillSummary {
        val subtotal = BillCalculator.calculateSubtotal(items.map {
            (it.variant?.price ?: it.item.basePrice) to it.quantity
        })

        var cgst = "0.0"
        var sgst = "0.0"
        var customTax = "0.0"

        if (profile?.gstEnabled == true) {
            val gst = BillCalculator.calculateGST(subtotal, profile.gstPercentage)
            cgst = gst.cgst
            sgst = gst.sgst
        } else if (profile?.customTaxPercentage != null && profile.customTaxPercentage > 0) {
            customTax = BillCalculator.calculateCustomTax(subtotal, profile.customTaxPercentage)
        }

        val total = BillCalculator.calculateTotal(subtotal, cgst, sgst, customTax)
        return BillSummary(subtotal, cgst, sgst, customTax, total)
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
        _cartItems.value = emptyList()
        _customerName.value = ""
        _customerWhatsapp.value = ""
        _orderType.value = "dine_in"
        _paymentMode.value = PaymentMode.UPI
        _partAmount1.value = "0.0"
        _partAmount2.value = "0.0"
        _persistedPaymentTotal.value = null
        _paymentRecovery.value = PaymentRecoveryAssessment.Empty
        _lastBill.value = null
        _error.value = null
        _printStatus.value = null
        clearGatewayResult()
        savedStateHandle[PENDING_ONLINE_BILL_ID] = null
    }

    fun setPaymentMode(mode: PaymentMode, p1: String = "0.0", p2: String = "0.0") {
        if (mode != PaymentMode.UPI &&
            mode != PaymentMode.PART_CASH_UPI &&
            mode != PaymentMode.PART_UPI_POS
        ) {
            clearGatewayResult()
        }
        _paymentMode.value = mode
        _partAmount1.value = p1.ifBlank { "0.0" }
        _partAmount2.value = p2.ifBlank { "0.0" }
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
        // Stale response — a newer session has started. Do not mutate state.
        if (!ownsRestorationAttempt(capturedGeneration)) return false

        val bill = billWithItems.bill
        savedStateHandle[PENDING_ONLINE_BILL_ID] = localBillId
        editingBillId = localBillId
        _lastBill.value = billWithItems
        _customerName.value = bill.customerName ?: ""
        _customerWhatsapp.value = bill.customerWhatsapp ?: ""
        _orderType.value = bill.orderType
        _paymentMode.value = PaymentMode.fromDbValue(bill.paymentMode)
        _partAmount1.value = bill.partAmount1
        _partAmount2.value = bill.partAmount2
        _persistedPaymentTotal.value = bill.totalAmount
        _paymentRecovery.value = billRepository.getPaymentRecoveryAssessment(localBillId)

        _cartItems.value = billWithItems.items.map { billItem ->
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
        _cartItems.value = emptyList()
        _customerName.value = ""
        _customerWhatsapp.value = ""
        _partAmount1.value = "0.0"
        _partAmount2.value = "0.0"
        _persistedPaymentTotal.value = null
        _paymentRecovery.value = PaymentRecoveryAssessment.Empty
        _paymentMode.value = PaymentMode.CASH
        Log.w(TAG, "Rejected pending online bill restoration for localBillId=$localBillId")
    }

    suspend fun createDraftOnlineBill(): Long? = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            if (_cartItems.value.isEmpty()) {
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

                val finalSummary = computeSummary(_cartItems.value, profile)
                _billSummary.value = finalSummary
if (!validatePaymentLimits(finalSummary.total, _paymentMode.value, _partAmount1.value, _partAmount2.value)) {
                    _isLoading.value = false
                    return@withLock null
                }
                // UPI QR generation and payment capture must work offline. Reserve the bill
                // number locally, then let background sync reconcile with the server later.
                val dailyCounter = restaurantRepository.incrementAndGetTerminalDailyCounter(terminalIdentity.terminalId)
                val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
                val today = java.time.LocalDate.now(zoneId).toString()
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
                    paymentMode = _paymentMode.value.dbValue,
                    partAmount1 = _partAmount1.value,
                    partAmount2 = _partAmount2.value,
                    paymentStatus = PaymentStatus.PENDING.dbValue,
                    orderStatus = OrderStatus.DRAFT.dbValue,
                    cancelReason = "",
                    createdBy = sessionManager.getActiveUserId(),
                    createdAt = createdAt,
                    paidAt = null,
                    lastResetDate = profile.lastResetDate ?: "",
                    publicToken = publicToken,
                    ownerUserId = sessionManager.getActiveUserId(),
                    ownerRestaurantId = sessionManager.getRestaurantId(),
                    operationId = operationId
                )

                val items = _cartItems.value.map { cartItem ->
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
                if (!validatePaymentLimits(bill.totalAmount, _paymentMode.value, _partAmount1.value, _partAmount2.value)) {
                    _isLoading.value = false
                    return@withLock false
                }

                val paymentOperationBase = bill.operationId
                    ?: "${bill.restaurantId}:${bill.terminalId}:${bill.publicToken}:finalize"
                val payments = buildPaymentEntities(
                    billId = localBillId,
                    paymentMode = _paymentMode.value,
                    totalAmount = bill.totalAmount,
                    partAmount1 = _partAmount1.value,
                    partAmount2 = _partAmount2.value,
                    operationBase = paymentOperationBase
                )
                val finalized = billRepository.finalizeOnlineBill(
                    billId = localBillId,
                    payments = payments,
                    completedAt = System.currentTimeMillis()
                )
                _lastBill.value = finalized.billWithItems
                _cartItems.value = emptyList()
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
            if (_cartItems.value.isEmpty()) {
                _error.value = "Add at least one item before completing the bill."
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

                val finalSummary = computeSummary(_cartItems.value, profile)
                _billSummary.value = finalSummary
                if (!validatePaymentLimits(finalSummary.total, _paymentMode.value, _partAmount1.value, _partAmount2.value)) {
                    _isLoading.value = false
                    return@withLock false
                }

                val dailyCounter = restaurantRepository.incrementAndGetTerminalDailyCounter(terminalIdentity.terminalId)
                val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
                val today = java.time.LocalDate.now(zoneId).toString()
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
                    paymentMode = _paymentMode.value.dbValue,
                    partAmount1 = _partAmount1.value,
                    partAmount2 = _partAmount2.value,
                    paymentStatus = status.dbValue,
                    orderStatus = if (status == PaymentStatus.SUCCESS) OrderStatus.COMPLETED.dbValue else OrderStatus.CANCELLED.dbValue,
                    cancelReason = if (status == PaymentStatus.FAILED) cancelReason else "",
                    createdBy = sessionManager.getActiveUserId(),
                    createdAt = createdAt,
                    paidAt = if (status == PaymentStatus.SUCCESS) System.currentTimeMillis() else null,
                    lastResetDate = profile.lastResetDate ?: "",
                    publicToken = publicToken,
                    ownerUserId = sessionManager.getActiveUserId(),
                    ownerRestaurantId = sessionManager.getRestaurantId(),
                    operationId = operationId
                )

                val items = _cartItems.value.map { cartItem ->
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

                val payments = buildPaymentEntities(
                    billId = 0,
                    paymentMode = _paymentMode.value,
                    totalAmount = finalSummary.total,
                    partAmount1 = _partAmount1.value,
                    partAmount2 = _partAmount2.value,
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
                _printStatus.value = null
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
                _cartItems.value = emptyList()
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
                    _cartItems.value = cartList
                    _billSummary.value = BillSummary(
                        subtotal = billWithItems.bill.subtotal,
                        cgst = billWithItems.bill.cgstAmount,
                        sgst = billWithItems.bill.sgstAmount,
                        customTax = billWithItems.bill.customTaxAmount,
                        total = billWithItems.bill.totalAmount
                    )
                    _persistedPaymentTotal.value = billWithItems.bill.totalAmount
                    _paymentRecovery.value =
                        billRepository.getPaymentRecoveryAssessment(billId)
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
        _cartItems.value = emptyList()
        _customerName.value = ""
        _customerWhatsapp.value = ""
        _orderType.value = "dine_in"
        _persistedPaymentTotal.value = null
        _paymentRecovery.value = PaymentRecoveryAssessment.Empty
    }

    suspend fun saveDraftOrder(tableName: String): Boolean = withContext(Dispatchers.IO) {
        orderMutex.withLock {
            if (_cartItems.value.isEmpty()) {
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

                val finalSummary = computeSummary(_cartItems.value, profile)
                _billSummary.value = finalSummary

                val dailyCounter = restaurantRepository.incrementAndGetTerminalDailyCounter(terminalIdentity.terminalId)
                val zoneId = java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)
                val today = java.time.LocalDate.now(zoneId).toString()
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
                    lastResetDate = profile.lastResetDate ?: "",
                    publicToken = publicToken,
                    ownerUserId = sessionManager.getActiveUserId(),
                    ownerRestaurantId = sessionManager.getRestaurantId(),
                    operationId = operationId
                )

                val items = _cartItems.value.map { cartItem ->
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
                _printStatus.value = null

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
                _cartItems.value = emptyList()
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
            if (_cartItems.value.isEmpty()) {
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
                val cartItems = _cartItems.value

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
                _printStatus.value = null

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
                _cartItems.value = emptyList()
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
                    _printStatus.value = null
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
                val payments = buildPaymentEntities(
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
                _printStatus.value = null

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
                _cartItems.value = emptyList()
                _persistedPaymentTotal.value = null
                _paymentRecovery.value = PaymentRecoveryAssessment.Empty
                editingBillId = null
                _printStatus.value = null
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
                _paymentRecovery.value = recovery
                if (recovery is PaymentRecoveryAssessment.Empty) {
                    _partAmount1.value = "0.0"
                    _partAmount2.value = "0.0"
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

    private fun buildPaymentEntities(
        billId: Long,
        paymentMode: PaymentMode,
        totalAmount: String,
        partAmount1: String,
        partAmount2: String,
        operationBase: String,
        deviceId: String = "",
        restaurantId: Long = 0
    ): List<BillPaymentEntity> {
        return PaymentModeManager.getPaymentComponents(
            mode = paymentMode,
            totalAmount = totalAmount,
            partAmount1 = partAmount1,
            partAmount2 = partAmount2
        ).map { component ->
            BillPaymentEntity(
                billId = billId,
                paymentMode = component.mode.dbValue,
                amount = component.amount,
                operationId = "$operationBase:payment:${component.mode.dbValue}",
                deviceId = deviceId,
                restaurantId = restaurantId,
                verifiedBy = "manual"
            )
        }
    }

    private fun validatePaymentLimits(
        total: String,
        mode: PaymentMode,
        partAmount1: String,
        partAmount2: String
    ): Boolean {
        val upiAmount = when (mode) {
            PaymentMode.UPI -> parseAmount(total)
            PaymentMode.PART_CASH_UPI -> parseAmount(partAmount2)
            PaymentMode.PART_UPI_POS -> parseAmount(partAmount1)
            else -> BigDecimal.ZERO
        }
        if (upiAmount > PaymentLimits.UPI_SINGLE_TRANSACTION_MAX) {
            _error.value = PaymentLimits.UPI_LIMIT_MESSAGE
            return false
        }
        return true
    }

    private fun parseAmount(value: String): BigDecimal {
        return value.ifBlank { "0" }.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    fun updateCartItemNote(item: MenuItemEntity, variant: ItemVariantEntity?, note: String) {
        _cartItems.update { current ->
            current.map {
                if (it.item.id == item.id && it.variant?.id == variant?.id) it.copy(note = note)
                else it
            }
        }
    }

    fun clearError() {
        _error.value = null
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
        if (_receiptPrinting.value) return
        if (bill.bill.orderStatus.equals(OrderStatus.CANCELLED.dbValue, ignoreCase = true)) {
            _error.value = "Cannot print receipt for a cancelled order."
            return
        }
        val profile = _cachedProfile.value ?: run {
            _error.value = "Restaurant profile not loaded."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _receiptPrinting.value = true
            try {
                val result = printRouter.printBill(bill, profile, PrintDispatchMode.MANUAL_RECEIPT_ONLY)
                if (result.attempted == 0 && result.failures.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(appContext, "Printer not connected. Opening PDF viewer.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    openBillPdfFallback(
                        bill = bill,
                        profile = profile,
                        statusMessage = appContext.getString(R.string.toast_printer_opening_pdf)
                    )
                } else if (result.failures.isNotEmpty()) {
                    if (result.succeeded > 0) {
                        withContext(Dispatchers.Main) {
                            _error.value = result.failures.joinToString()
                            _printStatus.value = "Receipt reprinted with some failures."
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(appContext, "Printer not connected. Opening PDF viewer.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        openBillPdfFallback(
                            bill = bill,
                            profile = profile,
                            statusMessage = appContext.getString(R.string.toast_printer_opening_pdf)
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _printStatus.value = "Receipt reprinted successfully."
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Manual receipt print failed", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(appContext, "Printer not connected. Opening PDF viewer.", android.widget.Toast.LENGTH_SHORT).show()
                }
                openBillPdfFallback(
                    bill = bill,
                    profile = profile,
                    statusMessage = appContext.getString(R.string.toast_printer_opening_pdf),
                    errorMessage = UserMessageSanitizer.sanitize(e, "Unable to print bill.")
                )
            } finally {
                _receiptPrinting.value = false
            }
        }
    }

    fun printKitchenTicket(bill: BillWithItems) {
        if (bill.bill.orderStatus.equals(OrderStatus.CANCELLED.dbValue, ignoreCase = true)) {
            _error.value = "Cannot print KDS for a cancelled order."
            return
        }
        val profile = _cachedProfile.value ?: run {
            _error.value = "Restaurant profile not loaded."
            return
        }

        if (!kitchenPrintInFlight.compareAndSet(false, true)) return
        _kitchenPrinting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = printRouter.printBill(bill, profile, PrintDispatchMode.MANUAL_KITCHEN_ONLY)
                if (result.attempted == 0) {
                    withContext(Dispatchers.Main) {
                        _error.value = "No kitchen printer configured."
                        _printStatus.value = "No KDS printer configured."
                    }
                } else if (result.failures.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _error.value = result.failures.joinToString()
                        _printStatus.value = if (result.succeeded > 0) {
                            "KDS reprinted with some failures."
                        } else {
                            "KDS reprint failed."
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _printStatus.value = "KDS reprinted successfully."
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Manual kitchen print failed", e)
                withContext(Dispatchers.Main) {
                    _error.value = UserMessageSanitizer.sanitize(e, "Unable to print kitchen ticket.")
                    _printStatus.value = "KDS reprint failed."
                }
            } finally {
                kitchenPrintInFlight.set(false)
                _kitchenPrinting.value = false
            }
        }
    }

    @Immutable
    data class CartItem(val item: MenuItemEntity, val variant: ItemVariantEntity? = null, val quantity: Int, val note: String = "")
    
    @Immutable
    data class BillSummary(val subtotal: String = "0.0", val cgst: String = "0.0", val sgst: String = "0.0", val customTax: String = "0.0", val total: String = "0.0")

    private fun buildPrintStatusMessage(prefix: String, targets: List<String>): String {
        val normalized = targets.distinct().map {
            when (it) {
                PrinterRole.CUSTOMER.name -> "customer receipt"
                PrinterRole.KITCHEN.name -> "kitchen ticket"
                else -> it.lowercase()
            }
        }
        return "$prefix ${normalized.joinToString(" and ")}."
    }

    private fun buildPartialPrintStatus(result: com.khanabook.lite.pos.domain.manager.PrintDispatchResult): String {
        val success = buildPrintStatusMessage("Printed", result.successTargets)
        val failureCount = result.failures.size
        return "$success $failureCount printer task${if (failureCount == 1) "" else "s"} failed."
    }

    private suspend fun openBillPdfFallback(
        bill: BillWithItems,
        profile: RestaurantProfileEntity?,
        statusMessage: String,
        errorMessage: String? = null
    ) {
        val pdfIntent = try {
            withContext(Dispatchers.IO) {
                val pdfFile = InvoicePDFGenerator(appContext).generatePDF(bill, profile)
                val pdfUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.provider",
                    pdfFile
                )
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(pdfUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _error.value = errorMessage
                _printStatus.value = null
                android.widget.Toast.makeText(
                    appContext,
                    UserMessageSanitizer.sanitize(e, "Unable to prepare invoice."),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        withContext(Dispatchers.Main) {
            _error.value = errorMessage
            _printStatus.value = statusMessage
            try {
                appContext.startActivity(Intent.createChooser(pdfIntent, "Open PDF to Print").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    appContext,
                    UserMessageSanitizer.sanitize(e, "Unable to open invoice."),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

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
