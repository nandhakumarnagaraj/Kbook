package com.khanabook.lite.pos.feature.reports.viewmodel

import com.khanabook.lite.pos.core.util.AppConstants

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.printing.data.KitchenPrintQueueRepository
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileRepository
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.feature.printing.domain.BluetoothPrinterManager
import com.khanabook.lite.pos.feature.printing.domain.KitchenPrintQueueManager
import com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import com.khanabook.lite.pos.feature.printing.domain.PrinterConnectionType
import com.khanabook.lite.pos.feature.printing.domain.connectionTargetKey
import com.khanabook.lite.pos.feature.printing.domain.connectionTypeValue
import com.khanabook.lite.pos.feature.printing.domain.isConnectionConfigured
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.core.feedback.UiMessage
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.time.LocalTime

@kotlinx.coroutines.ExperimentalCoroutinesApi
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val kitchenPrintQueueRepository: KitchenPrintQueueRepository,
    private val kitchenPrintQueueManager: KitchenPrintQueueManager,
    private val printerProfileRepository: PrinterProfileRepository,
    private val restaurantRepository: RestaurantRepository,
    private val printerManager: BluetoothPrinterManager,
    private val networkMonitor: com.khanabook.lite.pos.feature.sync.domain.NetworkMonitor,
    private val syncManager: com.khanabook.lite.pos.feature.sync.domain.SyncManager,
    private val sessionManager: com.khanabook.lite.pos.feature.auth.domain.SessionManager
) : ViewModel() {

    /** Owner-only surfaces (sync/clock technical warnings) are hidden from staff. */
    val isOwner: Boolean
        get() = sessionManager.isOwner()

    val clockDriftWarning: StateFlow<Boolean> = syncManager.clockDriftSeconds
        .map { drift -> (drift ?: 0L) > com.khanabook.lite.pos.feature.sync.domain.SyncManager.MAX_ALLOWED_CLOCK_DRIFT_SECONDS }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val profileFlow = billRepository.getProfileFlow()

    val connectionStatus: StateFlow<com.khanabook.lite.pos.feature.sync.domain.ConnectionStatus> = networkMonitor.status
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.khanabook.lite.pos.feature.sync.domain.ConnectionStatus.Unavailable
        )

    val unsyncedCount: StateFlow<Int> = billRepository.getUnsyncedCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val pendingOnlinePayments: StateFlow<List<com.khanabook.lite.pos.feature.billing.data.BillEntity>> =
        billRepository.getPendingOnlineBillsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val activeDraftBills: StateFlow<List<com.khanabook.lite.pos.feature.billing.data.BillEntity>> =
        billRepository.getActiveDraftBillsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val quarantinedSyncCount: StateFlow<Int> = billRepository.getSyncQuarantineCountFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val printerReadiness: StateFlow<PrinterReadiness> = combine(
        profileFlow,
        printerProfileRepository.getProfilesFlow(),
        printerManager.connectedDeviceMacs
    ) { profile, profiles, liveMacs ->
        val customerPrinter = profiles.firstOrNull {
            it.role == PrinterRole.CUSTOMER.name && it.enabled && it.isConnectionConfigured()
        }
        val kitchenPrinter = profiles.firstOrNull {
            it.role == PrinterRole.KITCHEN.name && it.enabled && it.isConnectionConfigured()
        }
        val legacyPrinterEnabled = profile?.printerEnabled == true && !profile.printerMac.isNullOrBlank()
        val legacyPrinterConnected = profile?.printerMac?.let(liveMacs::contains) == true

        PrinterReadiness(
            customerConfigured = customerPrinter != null,
            customerConnected = customerPrinter?.let {
                it.connectionTypeValue() == PrinterConnectionType.BLUETOOTH &&
                    liveMacs.contains(it.macAddress)
            } == true,
            customerName = customerPrinter?.name,
            customerAutoPrint = customerPrinter?.autoPrint == true,
            legacyReceiptConfigured = legacyPrinterEnabled,
            legacyReceiptConnected = legacyPrinterConnected,
            legacyReceiptName = profile?.printerName,
            legacyReceiptAutoPrint = profile?.autoPrintOnSuccess == true,
            kitchenConfigured = kitchenPrinter != null,
            kitchenConnected = kitchenPrinter?.let {
                it.connectionTypeValue() == PrinterConnectionType.BLUETOOTH &&
                    liveMacs.contains(it.macAddress)
            } == true,
            kitchenName = kitchenPrinter?.name,
            kitchenAutoPrint = kitchenPrinter?.autoPrint == true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PrinterReadiness()
    )

    /** Emits the shop name for a personalised greeting. Falls back to "Your Shop". */
    val shopName: StateFlow<String> = profileFlow
        .map { it?.shopName?.takeIf { n -> n.isNotBlank() } ?: "Your Shop" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Your Shop"
        )

    val orderPaymentFlowMode: StateFlow<OrderPaymentFlowMode> = profileFlow
        .map { OrderPaymentFlowMode.fromDbValue(it?.orderPaymentFlowMode) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OrderPaymentFlowMode.PAY_BEFORE_FOOD
        )

    /** True when the restaurant has turned off collecting the customer mobile number (quick billing). */
    val quickModeEnabled: StateFlow<Boolean> = profileFlow
        .map { it?.collectCustomerNumber == false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Time-aware greeting: Good Morning / Afternoon / Evening. */
    val greeting: String
        get() = when (LocalTime.now().hour) {
            in 5..11  -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else       -> "Good Evening"
        }

    val statsReady: StateFlow<Boolean> = profileFlow
        .filterNotNull()
        .map { true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _summaryScope = MutableStateFlow(SummaryScope.THIS_COUNTER)
    val summaryScope: StateFlow<SummaryScope> = _summaryScope.asStateFlow()

    fun setSummaryScope(scope: SummaryScope) {
        _summaryScope.value = scope
    }

    val todayStats: StateFlow<HomeStats> = combine(
        profileFlow.filterNotNull(),
        _summaryScope
    ) { profile, scope ->
        profile to scope
    }
        .flatMapLatest { (profile, scope) ->
            val zoneId = AppConstants.DEFAULT_TIMEZONE
            val today = java.time.LocalDate.now(java.time.ZoneId.of(zoneId)).toString()
            val start = com.khanabook.lite.pos.core.util.DateUtils.getStartOfDay(today, zoneId)
            val end = com.khanabook.lite.pos.core.util.DateUtils.getEndOfDay(today, zoneId)

            val billsFlow = when (scope) {
                SummaryScope.THIS_COUNTER -> billRepository.getBillsByDateRange(start, end)
                SummaryScope.SHOP_TOTAL -> billRepository.getShopBillsByDateRange(start, end)
            }
            
            combine(
                billsFlow,
                kitchenPrintQueueRepository.getPendingCountFlow()
            ) { bills, kdsPendingCount ->
                    val completedBills = bills.filter { it.orderStatus == "completed" || it.orderStatus == "paid" }
                    val totalRevenue = completedBills.sumOf { it.totalAmount.toDoubleOrNull() ?: 0.0 }
                    var cashRev = 0.0
                    var upiRev = 0.0
                    for (b in completedBills) {
                        val mode = com.khanabook.lite.pos.domain.model.PaymentMode.fromDbValue(b.paymentMode)
                        when (mode) {
                            com.khanabook.lite.pos.domain.model.PaymentMode.CASH -> {
                                cashRev += b.totalAmount.toDoubleOrNull() ?: 0.0
                            }
                            com.khanabook.lite.pos.domain.model.PaymentMode.UPI -> {
                                upiRev += b.totalAmount.toDoubleOrNull() ?: 0.0
                            }
                            com.khanabook.lite.pos.domain.model.PaymentMode.PART_CASH_UPI -> {
                                cashRev += b.partAmount1.toDoubleOrNull() ?: 0.0
                                upiRev += b.partAmount2.toDoubleOrNull() ?: 0.0
                            }
                            com.khanabook.lite.pos.domain.model.PaymentMode.PART_CASH_POS -> {
                                cashRev += b.partAmount1.toDoubleOrNull() ?: 0.0
                            }
                            com.khanabook.lite.pos.domain.model.PaymentMode.PART_UPI_POS -> {
                                upiRev += b.partAmount1.toDoubleOrNull() ?: 0.0
                            }
                            else -> {}
                        }
                    }
                    val cancelledCount = bills.count { it.orderStatus == "cancelled" }
                    val billedCustomers = bills
                        .filterNot { it.isDeleted }
                        .mapNotNull { it.customerWhatsapp?.takeIf(String::isNotBlank) }
                        .distinct()
                    HomeStats(
                        orderCount = bills.size,
                        revenue = totalRevenue,
                        cashRevenue = cashRev,
                        upiRevenue = upiRev,
                        customerCount = billedCustomers.size,
                        avgOrderValue = if (completedBills.isNotEmpty()) totalRevenue / completedBills.size else 0.0,
                        cancelledCount = cancelledCount,
                        kdsPendingCount = kdsPendingCount
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeStats()
        )

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    init {
        connectConfiguredPrinters(showMessage = false)
    }

    suspend fun reprintPendingKdsList(): List<com.khanabook.lite.pos.feature.billing.data.BillWithItems> =
        billRepository.getBillsWithPendingKds()

    suspend fun executeReprintPendingKds() {
        val pendingCount = kitchenPrintQueueRepository.getPendingCountFlow().first()
        if (pendingCount == 0) {
            _message.emit(UiMessage("No pending KDS tickets.", ToastKind.Info))
            return
        }

        val kitchenPrinter = printerProfileRepository.getProfiles().firstOrNull {
            it.role == PrinterRole.KITCHEN.name && it.enabled && it.isConnectionConfigured()
        }
        if (kitchenPrinter == null && printerManager.connectedDeviceMac.value.isNullOrBlank()) {
            _message.emit(UiMessage("No kitchen printer configured or connected.", ToastKind.Warning))
            return
        }

        kitchenPrintQueueManager.flushAllPending()
        val remainingCount = kitchenPrintQueueRepository.getPendingCountFlow().first()
        if (remainingCount == 0) {
            _message.emit(UiMessage("KDS tickets reprinted.", ToastKind.Success))
        } else {
            _message.emit(UiMessage("$remainingCount KDS ticket(s) still pending.", ToastKind.Warning))
        }
    }

    fun reprintPendingKds() {
        viewModelScope.launch {
            executeReprintPendingKds()
        }
    }

    fun cancelPendingOnlinePayment(billId: Long) {
        viewModelScope.launch {
            billRepository.cancelOrder(billId, "Payment attempt cancelled by cashier")
            _message.emit(UiMessage("Pending payment cancelled.", ToastKind.Success))
        }
    }

    fun updateOrderPaymentFlowMode(mode: OrderPaymentFlowMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = restaurantRepository.getProfile()
            if (current == null) {
                _message.emit(UiMessage("Restaurant profile is not ready.", ToastKind.Warning))
                return@launch
            }
            if (current.orderPaymentFlowMode == mode.dbValue) return@launch
            restaurantRepository.saveProfile(current.copy(orderPaymentFlowMode = mode.dbValue))
            _message.emit(UiMessage("${mode.displayLabel} enabled.", ToastKind.Success))
        }
    }

    fun refreshPrinterConnections() {
        connectConfiguredPrinters(showMessage = true)
    }

    private fun connectConfiguredPrinters(showMessage: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val configuredPrinters = printerProfileRepository.getProfiles().filter {
                it.enabled && it.isConnectionConfigured()
            }
            val printers = configuredPrinters.filter {
                it.enabled &&
                    it.connectionTypeValue() == PrinterConnectionType.BLUETOOTH &&
                    it.macAddress.isNotBlank()
            }
            val legacyPrinter = profileFlow.first()?.takeIf {
                it.printerEnabled && !it.printerMac.isNullOrBlank()
            }
            if (printers.isEmpty()) {
                if (configuredPrinters.any { it.connectionTypeValue() == PrinterConnectionType.WIFI }) {
                    if (showMessage) {
                        _message.emit(UiMessage("Wi-Fi printer configured.", ToastKind.Success))
                    }
                    return@launch
                }
                if (configuredPrinters.any { it.connectionTypeValue() == PrinterConnectionType.USB }) {
                    if (showMessage) {
                        _message.emit(UiMessage("USB printer configured.", ToastKind.Success))
                    }
                    return@launch
                }
                val legacyMac = legacyPrinter?.printerMac?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
                if (legacyMac == null) {
                    if (showMessage) _message.emit(UiMessage("No printer configured.", ToastKind.Warning))
                    return@launch
                }
                val connected = printerManager.isConnectedTo(legacyMac) ||
                    printerManager.connect(legacyMac)
                if (showMessage) {
                    _message.emit(
                        if (connected) {
                            UiMessage("Printer connected.", ToastKind.Success)
                        } else {
                            UiMessage("Couldn't connect printer.", ToastKind.Error)
                        }
                    )
                }
                return@launch
            }
            val results = printers.map { printer ->
                async {
                    printerManager.isConnectedTo(printer.macAddress) ||
                        printerManager.connect(printer.macAddress)
                }
            }.awaitAll()
            if (showMessage) {
                val connectedCount = results.count { it }
                _message.emit(
                    when {
                        connectedCount == printers.size ->
                            UiMessage("Printers connected.", ToastKind.Success)
                        connectedCount == 0 ->
                            UiMessage("Couldn't connect configured printers.", ToastKind.Error)
                        else ->
                            UiMessage("$connectedCount of ${printers.size} printers connected.", ToastKind.Warning)
                    }
                )
            }
        }
    }


    enum class SummaryScope {
        THIS_COUNTER,
        SHOP_TOTAL
    }

    @Immutable
    data class HomeStats(
        val orderCount: Int = 0,
        val revenue: Double = 0.0,
        val cashRevenue: Double = 0.0,
        val upiRevenue: Double = 0.0,
        val customerCount: Int = 0,
        val avgOrderValue: Double = 0.0,
        val cancelledCount: Int = 0,
        val kdsPendingCount: Int = 0
    )

    data class PrinterReadiness(
        val customerConfigured: Boolean = false,
        val customerConnected: Boolean = false,
        val customerName: String? = null,
        val customerAutoPrint: Boolean = false,
        val legacyReceiptConfigured: Boolean = false,
        val legacyReceiptConnected: Boolean = false,
        val legacyReceiptName: String? = null,
        val legacyReceiptAutoPrint: Boolean = false,
        val kitchenConfigured: Boolean = false,
        val kitchenConnected: Boolean = false,
        val kitchenName: String? = null,
        val kitchenAutoPrint: Boolean = false
    )
}
