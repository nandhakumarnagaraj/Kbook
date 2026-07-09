package com.khanabook.lite.pos.ui.viewmodel

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
            it.role == PrinterRole.CUSTOMER.name && it.enabled && it.macAddress.isNotBlank()
        }
        val kitchenPrinter = profiles.firstOrNull {
            it.role == PrinterRole.KITCHEN.name && it.enabled && it.macAddress.isNotBlank()
        }
        val legacyPrinterEnabled = profile?.printerEnabled == true && !profile.printerMac.isNullOrBlank()
        val legacyPrinterConnected = profile?.printerMac?.let(liveMacs::contains) == true

        PrinterReadiness(
            customerConfigured = customerPrinter != null,
            customerConnected = customerPrinter?.macAddress?.let(liveMacs::contains) == true,
            customerName = customerPrinter?.name,
            customerAutoPrint = customerPrinter?.autoPrint == true,
            legacyReceiptConfigured = legacyPrinterEnabled,
            legacyReceiptConnected = legacyPrinterConnected,
            legacyReceiptName = profile?.printerName,
            legacyReceiptAutoPrint = profile?.autoPrintOnSuccess == true,
            kitchenConfigured = kitchenPrinter != null,
            kitchenConnected = kitchenPrinter?.macAddress?.let(liveMacs::contains) == true,
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
            val zoneId = "Asia/Kolkata"
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

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        connectConfiguredPrinters(showMessage = false)
    }

    suspend fun reprintPendingKdsList(): List<com.khanabook.lite.pos.data.local.relation.BillWithItems> =
        billRepository.getBillsWithPendingKds()

    fun reprintPendingKds() {
        viewModelScope.launch {
            val pendingCount = kitchenPrintQueueRepository.getPendingCountFlow().first()
            if (pendingCount == 0) {
                _message.emit("No pending KDS tickets.")
                return@launch
            }

            val kitchenPrinter = printerProfileRepository.getProfiles().firstOrNull {
                it.role == PrinterRole.KITCHEN.name && it.enabled && it.macAddress.isNotBlank()
            }
            if (kitchenPrinter == null) {
                _message.emit("No kitchen printer configured.")
                return@launch
            }

            kitchenPrintQueueManager.flushPendingForPrinter(kitchenPrinter.macAddress)
            val remainingCount = kitchenPrintQueueRepository.getPendingCountFlow().first()
            if (remainingCount == 0) {
                _message.emit("KDS tickets reprinted.")
            } else {
                _message.emit("$remainingCount KDS ticket(s) still pending.")
            }
        }
    }

    fun cancelPendingOnlinePayment(billId: Long) {
        viewModelScope.launch {
            billRepository.cancelOrder(billId, "Payment attempt cancelled by cashier")
            _message.emit("Pending payment cancelled.")
        }
    }

    fun updateOrderPaymentFlowMode(mode: OrderPaymentFlowMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = restaurantRepository.getProfile()
            if (current == null) {
                _message.emit("Restaurant profile is not ready.")
                return@launch
            }
            if (current.orderPaymentFlowMode == mode.dbValue) return@launch
            restaurantRepository.saveProfile(current.copy(orderPaymentFlowMode = mode.dbValue))
            _message.emit("${mode.displayLabel} enabled.")
        }
    }

    fun refreshPrinterConnections() {
        connectConfiguredPrinters(showMessage = true)
    }

    private fun connectConfiguredPrinters(showMessage: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val printers = printerProfileRepository.getProfiles().filter {
                it.enabled && it.macAddress.isNotBlank()
            }
            val legacyPrinter = profileFlow.first()?.takeIf {
                it.printerEnabled && !it.printerMac.isNullOrBlank()
            }
            if (printers.isEmpty()) {
                if (legacyPrinter == null) {
                    if (showMessage) _message.emit("No printer configured.")
                    return@launch
                }
                if (showMessage) _message.emit("Checking printer connection.")
                if (!printerManager.isConnectedTo(legacyPrinter.printerMac!!)) {
                    printerManager.connect(legacyPrinter.printerMac!!)
                }
                return@launch
            }
            if (showMessage) _message.emit("Checking printer connection.")
            printers.forEach { printer ->
                launch {
                    if (!printerManager.isConnectedTo(printer.macAddress)) {
                        printerManager.connect(printer.macAddress)
                    }
                }
            }
        }
    }


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
