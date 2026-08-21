package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.domain.util.AppConstants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager
import com.khanabook.lite.pos.domain.manager.KitchenPrintQueueManager
import com.khanabook.lite.pos.domain.model.OrderPaymentFlowMode
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.model.PrinterConnectionType
import com.khanabook.lite.pos.domain.model.connectionTargetKey
import com.khanabook.lite.pos.domain.model.connectionTypeValue
import com.khanabook.lite.pos.domain.model.isConnectionConfigured
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.feedback.UiMessage
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
    private val networkMonitor: com.khanabook.lite.pos.domain.util.NetworkMonitor
) : ViewModel() {

    private val profileFlow = billRepository.getProfileFlow()

    val connectionStatus: StateFlow<com.khanabook.lite.pos.domain.util.ConnectionStatus> = networkMonitor.status
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.khanabook.lite.pos.domain.util.ConnectionStatus.Unavailable
        )

    val unsyncedCount: StateFlow<Int> = billRepository.getUnsyncedCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val pendingOnlinePayments: StateFlow<List<com.khanabook.lite.pos.data.local.entity.BillEntity>> =
        billRepository.getPendingOnlineBillsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val activeDraftBills: StateFlow<List<com.khanabook.lite.pos.data.local.entity.BillEntity>> =
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

    val todayStats: StateFlow<HomeStats> = profileFlow
        .filterNotNull()
        .flatMapLatest { profile: com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity ->
            val zoneId = AppConstants.DEFAULT_TIMEZONE
            val today = java.time.LocalDate.now(java.time.ZoneId.of(zoneId)).toString()
            val start = com.khanabook.lite.pos.domain.util.DateUtils.getStartOfDay(today, zoneId)
            val end = com.khanabook.lite.pos.domain.util.DateUtils.getEndOfDay(today, zoneId)
            
            combine(
                billRepository.getBillsByDateRange(start, end),
                kitchenPrintQueueRepository.getPendingCountFlow()
            ) { bills, kdsPendingCount ->
                    val completedBills = bills.filter { it.orderStatus == "completed" || it.orderStatus == "paid" }
                    val totalRevenue = completedBills.sumOf { it.totalAmount.toDoubleOrNull() ?: 0.0 }
                    val cancelledCount = bills.count { it.orderStatus == "cancelled" }
                    val billedCustomers = bills
                        .filterNot { it.isDeleted }
                        .mapNotNull { it.customerWhatsapp?.takeIf(String::isNotBlank) }
                        .distinct()
                    HomeStats(
                        orderCount = bills.size,
                        revenue = totalRevenue,
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

    suspend fun reprintPendingKdsList(): List<com.khanabook.lite.pos.data.local.relation.BillWithItems> =
        billRepository.getBillsWithPendingKds()

    fun reprintPendingKds() {
        viewModelScope.launch {
            val pendingCount = kitchenPrintQueueRepository.getPendingCountFlow().first()
            if (pendingCount == 0) {
                _message.emit(UiMessage("No pending KDS tickets.", ToastKind.Info))
                return@launch
            }

            val kitchenPrinter = printerProfileRepository.getProfiles().firstOrNull {
                it.role == PrinterRole.KITCHEN.name && it.enabled && it.isConnectionConfigured()
            }
            if (kitchenPrinter == null) {
                _message.emit(UiMessage("No kitchen printer configured.", ToastKind.Warning))
                return@launch
            }

            kitchenPrintQueueManager.flushPendingForPrinter(kitchenPrinter.connectionTargetKey())
            val remainingCount = kitchenPrintQueueRepository.getPendingCountFlow().first()
            if (remainingCount == 0) {
                _message.emit(UiMessage("KDS tickets reprinted.", ToastKind.Success))
            } else {
                _message.emit(UiMessage("$remainingCount KDS ticket(s) still pending.", ToastKind.Warning))
            }
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
                if (legacyPrinter == null) {
                    if (showMessage) _message.emit(UiMessage("No printer configured.", ToastKind.Warning))
                    return@launch
                }
                val mac = legacyPrinter.printerMac ?: return@launch
                val connected = printerManager.isConnectedTo(mac) ||
                    printerManager.connect(mac)
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


    @Immutable
    data class HomeStats(
        val orderCount: Int = 0,
        val revenue: Double = 0.0,
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
