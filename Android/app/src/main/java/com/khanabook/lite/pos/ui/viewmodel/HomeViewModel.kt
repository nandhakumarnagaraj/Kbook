package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.domain.manager.KitchenPrintQueueManager
import com.khanabook.lite.pos.domain.model.PrinterRole
import dagger.hilt.android.lifecycle.HiltViewModel
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

    /** Emits the shop name for a personalised greeting. Falls back to "Your Shop". */
    val shopName: StateFlow<String> = profileFlow
        .map { it?.shopName?.takeIf { n -> n.isNotBlank() } ?: "Your Shop" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Your Shop"
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
                    HomeStats(
                        orderCount = bills.size,
                        revenue = totalRevenue,
                        customerCount = completedBills.mapNotNull { it.customerWhatsapp }.distinct().size,
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


    data class HomeStats(
        val orderCount: Int = 0,
        val revenue: Double = 0.0,
        val customerCount: Int = 0,
        val avgOrderValue: Double = 0.0,
        val cancelledCount: Int = 0,
        val kdsPendingCount: Int = 0
    )
}


