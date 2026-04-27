package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.repository.MenuRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.domain.manager.BillCalculator
import com.khanabook.lite.pos.domain.manager.OrderIdManager
import com.khanabook.lite.pos.domain.manager.PrintDispatchMode
import com.khanabook.lite.pos.domain.manager.PrintRouter
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.openBillToPrint
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
    val easebuzzClient: com.khanabook.lite.pos.domain.manager.EasebuzzClient
) : ViewModel() {

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
    }

    private val orderMutex = Mutex()

    // Cache the restaurant profile reactively — avoids repeated DB reads in updateSummary
    // and completeOrder. Stays automatically fresh because it's backed by a Flow.
    private val _cachedProfile: StateFlow<RestaurantProfileEntity?> =
        restaurantRepository.getProfileFlow()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems

    init {
        // Recompute summary whenever cart changes (debounced) OR profile changes
        combine(_cartItems.debounce(300), _cachedProfile) { items, profile ->
            computeSummary(items, profile)
        }
            .onEach { _billSummary.value = it }
            .launchIn(viewModelScope)
    }

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName

    private val _customerWhatsapp = MutableStateFlow("")
    val customerWhatsapp: StateFlow<String> = _customerWhatsapp

    private val _recentCustomers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val recentCustomers: StateFlow<List<Pair<String, String>>> = _recentCustomers

    fun loadRecentCustomers() {
        viewModelScope.launch {
            _recentCustomers.value = billRepository.getRecentCustomers()
        }
    }

    private val _paymentMode = MutableStateFlow(PaymentMode.UPI)
    val paymentMode: StateFlow<PaymentMode> = _paymentMode

    private val _partAmount1 = MutableStateFlow("0.0")
    private val _partAmount2 = MutableStateFlow("0.0")

    private val _billSummary = MutableStateFlow(BillSummary())
    val billSummary: StateFlow<BillSummary> = _billSummary
    
    private val _lastBill = MutableStateFlow<BillWithItems?>(null)
    val lastBill: StateFlow<BillWithItems?> = _lastBill

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _printStatus = MutableStateFlow<String?>(null)
    val printStatus: StateFlow<String?> = _printStatus

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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

    fun setCustomerInfo(name: String, whatsapp: String) {
        _customerName.value = name
        _customerWhatsapp.value = whatsapp
    }

    fun setPaymentMode(mode: PaymentMode, p1: String = "0.0", p2: String = "0.0") {
        _paymentMode.value = mode
        _partAmount1.value = p1.ifBlank { "0.0" }
        _partAmount2.value = p2.ifBlank { "0.0" }
    }

    suspend fun getLatestPendingOnlineBillId(): Long? {
        return billRepository.getLatestPendingOnlineBill()?.id
    }

    suspend fun restorePendingOnlineBill(localBillId: Long): Boolean {
        val bill = billRepository.getBillById(localBillId) ?: return false
        _paymentMode.value = PaymentMode.fromDbValue(bill.paymentMode)
        _partAmount1.value = bill.partAmount1
        _partAmount2.value = bill.partAmount2
        return true
    }

    suspend fun createDraftOnlineBill(): Long? = orderMutex.withLock {
        if (_cartItems.value.isEmpty()) {
            _error.value = "Add at least one item before starting payment."
            return null
        }
        _isLoading.value = true
        try {
            val profile = _cachedProfile.value ?: restaurantRepository.getProfile() ?: return null
            val restaurantId = sessionManager.getRestaurantId()
            if (restaurantId == 0L) {
                _error.value = "Account not set up. Please log out and log in again."
                return null
            }

            val finalSummary = _billSummary.value
            val (dailyCounter, lifetimeId) = restaurantRepository.incrementAndGetCounters()
            val zoneId = try {
                java.time.ZoneId.of(profile.timezone ?: "Asia/Kolkata")
            } catch (e: Exception) {
                java.time.ZoneId.of("Asia/Kolkata")
            }
            val today = java.time.LocalDate.now(zoneId).toString()
            val displayId = OrderIdManager.getDailyOrderDisplay(today, dailyCounter)

            val bill = BillEntity(
                restaurantId = restaurantId,
                deviceId = sessionManager.getDeviceId(),
                dailyOrderId = dailyCounter,
                dailyOrderDisplay = displayId,
                lifetimeOrderId = lifetimeId,
                orderType = "order",
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
                createdAt = System.currentTimeMillis(),
                paidAt = null,
                lastResetDate = profile.lastResetDate ?: ""
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

            billRepository.insertFullBill(bill, items, emptyList())
            val inserted = billRepository.getBillWithItemsByLifetimeId(lifetimeId)
            _lastBill.value = inserted

            // Pre-flight: ensure profile.restaurantId is set; sync's pushAll() returns false silently otherwise.
            val profileForSync = restaurantRepository.getProfile()
            if (profileForSync == null || (profileForSync.restaurantId ?: 0L) <= 0L) {
                _error.value = "Account setup incomplete. Please log out and log in again to refresh your profile."
                _isLoading.value = false
                return null
            }

            var lastError: Throwable? = null
            var synced = false
            for (attempt in 1..3) {
                val result = syncManager.pushUnsyncedDataWithResult()
                if (result.isSuccess) { synced = true; break }
                lastError = result.exceptionOrNull()
                Log.w(TAG, "Sync attempt $attempt failed", lastError)
                if (attempt < 3) kotlinx.coroutines.delay(attempt * 1000L)
            }
            if (!synced) {
                _error.value = describeSyncFailure(lastError)
                _isLoading.value = false
                return null
            }

            _isLoading.value = false
            inserted?.bill?.id
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

    suspend fun finalizeOnlineBill(localBillId: Long, status: PaymentStatus, cancelReason: String = ""): Boolean = orderMutex.withLock {
        _isLoading.value = true
        try {
            val bill = billRepository.getBillById(localBillId) ?: run {
                _error.value = "Bill not found."
                _isLoading.value = false
                return false
            }
            val profile = _cachedProfile.value ?: restaurantRepository.getProfile()
            val updatedBill = bill.copy(
                paymentStatus = status.dbValue,
                orderStatus = if (status == PaymentStatus.SUCCESS) OrderStatus.COMPLETED.dbValue else OrderStatus.CANCELLED.dbValue,
                cancelReason = if (status == PaymentStatus.SUCCESS) "" else cancelReason,
                paidAt = if (status == PaymentStatus.SUCCESS) System.currentTimeMillis() else null,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
            billRepository.updateBill(updatedBill)

            val gTxn = _gatewayTxnId.value
            val gStatus = _gatewayStatus.value
            val verifiedBy = if (gTxn != null) "easebuzz" else "manual"
            val payments = when (_paymentMode.value) {
                PaymentMode.PART_CASH_UPI -> listOf(
                    BillPaymentEntity(
                        billId = localBillId,
                        paymentMode = PaymentMode.CASH.dbValue,
                        amount = _partAmount1.value
                    ),
                    BillPaymentEntity(
                        billId = localBillId,
                        paymentMode = PaymentMode.UPI.dbValue,
                        amount = _partAmount2.value,
                        gatewayTxnId = gTxn,
                        gatewayStatus = gStatus,
                        verifiedBy = verifiedBy
                    )
                )
                PaymentMode.PART_UPI_POS -> listOf(
                    BillPaymentEntity(
                        billId = localBillId,
                        paymentMode = PaymentMode.UPI.dbValue,
                        amount = _partAmount1.value,
                        gatewayTxnId = gTxn,
                        gatewayStatus = gStatus,
                        verifiedBy = verifiedBy
                    ),
                    BillPaymentEntity(
                        billId = localBillId,
                        paymentMode = PaymentMode.POS.dbValue,
                        amount = _partAmount2.value
                    )
                )
                PaymentMode.UPI -> listOf(
                    BillPaymentEntity(
                        billId = localBillId,
                        paymentMode = PaymentMode.UPI.dbValue,
                        amount = bill.totalAmount,
                        gatewayTxnId = gTxn,
                        gatewayStatus = gStatus,
                        verifiedBy = verifiedBy
                    )
                )
                else -> listOf(
                    BillPaymentEntity(
                        billId = localBillId,
                        paymentMode = _paymentMode.value.dbValue,
                        amount = bill.totalAmount,
                        gatewayTxnId = gTxn,
                        gatewayStatus = gStatus,
                        verifiedBy = verifiedBy
                    )
                )
            }
            payments.forEach { billRepository.addBillPayment(it.copy(restaurantId = bill.restaurantId, deviceId = bill.deviceId)) }

            val updated = billRepository.getBillWithItemsById(localBillId)
            _lastBill.value = updated
            if (status == PaymentStatus.SUCCESS) {
                _cartItems.value = emptyList()
            }

            viewModelScope.launch(Dispatchers.IO) {
                runCatching { syncManager.pushUnsyncedDataImmediately() }
                    .onFailure { Log.w(TAG, "Post-finalization sync failed; will retry on next cycle", it) }
            }

            _isLoading.value = false
            true
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

    suspend fun completeOrder(status: PaymentStatus, cancelReason: String = ""): Boolean = orderMutex.withLock {
        if (_cartItems.value.isEmpty()) {
            _error.value = "Add at least one item before completing the bill."
            return false
        }
        _isLoading.value = true
        try {
            // Use cached profile — no extra DB read needed
            val profile = _cachedProfile.value ?: restaurantRepository.getProfile() ?: return false

            val restaurantId = sessionManager.getRestaurantId()
            if (restaurantId == 0L) {
                _error.value = "Account not set up. Please log out and log in again."
                return false
            }

            // Re-use the already-computed summary (produced by the debounced cart+profile combine)
            // instead of recalculating subtotal/tax/total from scratch.
            val finalSummary = _billSummary.value

            val (dailyCounter, lifetimeId) = restaurantRepository.incrementAndGetCounters()
            val zoneId = try {
                java.time.ZoneId.of(profile.timezone ?: "Asia/Kolkata")
            } catch (e: Exception) {
                java.time.ZoneId.of("Asia/Kolkata")
            }
            val today = java.time.LocalDate.now(zoneId).toString()
            val displayId = OrderIdManager.getDailyOrderDisplay(today, dailyCounter)

            val bill = BillEntity(
                restaurantId = sessionManager.getRestaurantId(),
                deviceId = sessionManager.getDeviceId(),
                dailyOrderId = dailyCounter,
                dailyOrderDisplay = displayId,
                lifetimeOrderId = lifetimeId,
                orderType = "order",
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
                createdAt = System.currentTimeMillis(),
                paidAt = if (status == PaymentStatus.SUCCESS) System.currentTimeMillis() else null,
                lastResetDate = profile.lastResetDate ?: ""
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

            // Gateway data is attached only to UPI rows; cash/POS rows stay manual.
            val gTxn = _gatewayTxnId.value
            val gStatus = _gatewayStatus.value
            val verifiedBy = if (gTxn != null) "easebuzz" else "manual"
            fun upi(amount: String) = BillPaymentEntity(
                billId = 0,
                paymentMode = PaymentMode.UPI.dbValue,
                amount = amount,
                gatewayTxnId = gTxn,
                gatewayStatus = gStatus,
                verifiedBy = verifiedBy
            )
            val payments = when (_paymentMode.value) {
                PaymentMode.PART_CASH_UPI -> listOf(
                    BillPaymentEntity(billId = 0, paymentMode = PaymentMode.CASH.dbValue, amount = _partAmount1.value),
                    upi(_partAmount2.value)
                )
                PaymentMode.PART_CASH_POS -> listOf(
                    BillPaymentEntity(billId = 0, paymentMode = PaymentMode.CASH.dbValue, amount = _partAmount1.value),
                    BillPaymentEntity(billId = 0, paymentMode = PaymentMode.POS.dbValue, amount = _partAmount2.value)
                )
                PaymentMode.PART_UPI_POS -> listOf(
                    upi(_partAmount1.value),
                    BillPaymentEntity(billId = 0, paymentMode = PaymentMode.POS.dbValue, amount = _partAmount2.value)
                )
                PaymentMode.UPI -> listOf(upi(finalSummary.total))
                else -> listOf(
                    BillPaymentEntity(billId = 0, paymentMode = _paymentMode.value.dbValue, amount = finalSummary.total)
                )
            }

            billRepository.insertFullBill(bill, items, payments)
            val inserted = billRepository.getBillWithItemsByLifetimeId(lifetimeId)
            _lastBill.value = inserted
            _printStatus.value = null

            // Trigger sync immediately so UI doesn't sit on "Syncing..." for 15 minutes
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val synced = syncManager.pushUnsyncedDataImmediately()
                    if (!synced) {
                        Log.w(TAG, "Bill saved locally but immediate sync did not complete")
                        withContext(Dispatchers.Main) {
                            _error.value = "Bill saved locally. Sync is pending."
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Immediate bill sync failed after save", e)
                    withContext(Dispatchers.Main) {
                        _error.value = "Bill saved locally. Sync failed and will retry."
                    }
                }
            }

            // Launch auto-print asynchronously — never blocks bill completion
            if (inserted != null && status == PaymentStatus.SUCCESS) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val result = printRouter.printBill(inserted, profile, PrintDispatchMode.AUTO)
                        val kitchenQueued = result.kitchenQueued ||
                            kitchenPrintQueueRepository.hasPendingForBill(inserted.bill.id)
                        if (result.attempted == 0) {
                            withContext(Dispatchers.Main) {
                                _printStatus.value = if (kitchenQueued) {
                                    when (result.kitchenQueueReason) {
                                        "not_configured" ->
                                            "Kitchen printer not configured. Bill saved. KDS queued."
                                        else ->
                                            "Kitchen printer offline. Bill saved. KDS queued."
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
                                        "Kitchen printer not configured. Bill saved. KDS queued."
                                    else ->
                                        "Kitchen printer offline. Bill saved. KDS queued."
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
                    } catch (e: Exception) {
                        Log.w(TAG, "Auto-print failed", e)
                        withContext(Dispatchers.Main) {
                            _error.value = UserMessageSanitizer.sanitize(
                                e,
                                "Unable to print invoice. Bill saved successfully."
                            )
                        }
                    }
                }
            }

            // Clearing the cart automatically triggers the combine{} → new BillSummary(empty)
            // No need to call updateSummary() manually.
            _cartItems.value = emptyList()
            _error.value = null
            _isLoading.value = false
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bill", e)
            _error.value = UserMessageSanitizer.sanitize(
                e,
                "Failed to save bill. Please try again."
            )
            _isLoading.value = false
            return false
        }
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

    fun printReceipt(bill: BillWithItems) {
        if (bill.bill.orderStatus.equals(OrderStatus.CANCELLED.dbValue, ignoreCase = true)) {
            _error.value = "Cannot print receipt for a cancelled order."
            return
        }
        val profile = _cachedProfile.value ?: run {
            _error.value = "Restaurant profile not loaded."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = printRouter.printBill(bill, profile, PrintDispatchMode.MANUAL_RECEIPT_ONLY)
                if (result.attempted == 0) {
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
                openBillPdfFallback(
                    bill = bill,
                    profile = profile,
                    statusMessage = appContext.getString(R.string.toast_printer_opening_pdf),
                    errorMessage = UserMessageSanitizer.sanitize(e, "Unable to print bill.")
                )
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
        withContext(Dispatchers.Main) {
            _error.value = errorMessage
            _printStatus.value = statusMessage
            openBillToPrint(appContext, bill, profile)
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
