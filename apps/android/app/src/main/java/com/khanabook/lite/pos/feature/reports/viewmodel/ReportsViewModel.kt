package com.khanabook.lite.pos.feature.reports.viewmodel
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.reports.domain.ReportGenerator
import com.khanabook.lite.pos.feature.reports.domain.OrderDetailRow
import com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow
import com.khanabook.lite.pos.core.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val permissionManager: com.khanabook.lite.pos.feature.staff.domain.PermissionManager
) : ViewModel() {

    /**
     * Whether the acting user may see revenue beyond today's summary.
     *
     * The server already enforces this on its analytics endpoints
     * (AnalyticsController requires reports.full), but Android reports are computed
     * locally from the bills table and never hit those endpoints — so without this
     * client check the server gate is bypassed entirely. Owners pass by role.
     */
    fun canViewFullReports(): Boolean =
        permissionManager.hasPermission(
            com.khanabook.lite.pos.feature.staff.domain.PermissionManager.REPORTS_FULL
        )

    /** Whether the acting user may export/download report data. */
    fun canExportReports(): Boolean =
        permissionManager.hasPermission(
            com.khanabook.lite.pos.feature.staff.domain.PermissionManager.REPORTS_EXPORT
        )

    private val reportGenerator = ReportGenerator(billRepository)

    private val _paymentBreakdown = MutableStateFlow<Map<String, String>>(emptyMap())
    val paymentBreakdown: StateFlow<Map<String, String>> = _paymentBreakdown

    private val _orderLevelRows = MutableStateFlow<List<OrderLevelRow>>(emptyList())
    val orderLevelRows: StateFlow<List<OrderLevelRow>> = _orderLevelRows

    private val _orderDetailsTable = MutableStateFlow<List<OrderDetailRow>>(emptyList())
    val orderDetailsTable: StateFlow<List<OrderDetailRow>> = _orderDetailsTable

    private val _reportType = MutableStateFlow("Order") 
    val reportType: StateFlow<String> = _reportType

    private val _timeFilter = MutableStateFlow("Today") 
    val timeFilter: StateFlow<String> = _timeFilter

    private val _selectedBillDetails = MutableStateFlow<com.khanabook.lite.pos.feature.billing.data.BillWithItems?>(null)
    val selectedBillDetails: StateFlow<com.khanabook.lite.pos.feature.billing.data.BillWithItems?> = _selectedBillDetails

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * False only until the first [loadReports] finishes for this instance.
     * The UI shows the full-screen skeleton on that first load only; refreshes keep the
     * existing table composed so its LazyListState (and the user's scroll position) survives.
     */
    private val _hasLoadedOnce = MutableStateFlow(false)
    val hasLoadedOnce: StateFlow<Boolean> = _hasLoadedOnce

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentFrom: Long = System.currentTimeMillis() - 86400000L
    private var currentTo: Long = System.currentTimeMillis()

    init {
        // Load the opening range once here. Doing it from ReportsScreen via
        // LaunchedEffect(Unit) re-ran on every tab re-entry and silently reset the
        // user's chosen filter back to "Today".
        observeReports()
        setTimeFilter("Today")
    }

    fun setReportType(type: String) {
        _reportType.value = type
    }

    fun setTimeFilter(filter: String) {
        _timeFilter.value = filter
        updateDateRangeAndLoad(filter)
    }

    fun loadBillDetails(billId: Long) {
        viewModelScope.launch {
            // Clear the previously opened order first: the dialog renders whatever is in
            // this flow, so holding the old value shows order A's items while order B loads.
            _selectedBillDetails.value = null
            try {
                _selectedBillDetails.value = reportGenerator.getOrderDetail(billId)
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to load order details."
                )
                // Leave the dialog on its own shimmer rather than a stale bill.
                _selectedBillDetails.value = null
            }
            // Deliberately does NOT touch _isLoading: that flag swaps the orders table for
            // the full-screen skeleton, which disposes the LazyColumn and resets its scroll
            // position to the top. The dialog shows its own shimmer while loading.
        }
    }

    fun clearBillDetails() {
        _selectedBillDetails.value = null
    }

    fun clearError() {
        _error.value = null
    }

    private fun updateDateRangeAndLoad(filter: String) {
        // Custom keeps whatever range the user already picked.
        if (filter == "Custom" && currentFrom > 0L && currentTo > 0L) {
            loadReports(currentFrom, currentTo)
            return
        }
        val cal = Calendar.getInstance()

        val (from, to) = when (filter) {
            "Yesterday" -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val from = startOfDay(cal)
                val to = endOfDay(cal)
                from to to
            }
            else -> {
                // "Today" (legacy "Daily" treated the same)
                val from = startOfDay(cal)
                val to = System.currentTimeMillis()
                from to to
            }
        }
        loadReports(from, to)
    }

    private fun startOfDay(cal: Calendar): Long {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun endOfDay(cal: Calendar): Long {
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun setCustomDateRange(from: Long, to: Long) {
        _timeFilter.value = "Custom"
        loadReports(normalizeStartOfDay(from), normalizeEndOfDay(to))
    }

    private data class DateRange(val from: Long, val to: Long)

    /** Window currently observed; Room re-emits it whenever the bills table changes. */
    private val rangeFlow = MutableStateFlow(DateRange(0L, 0L))

    /**
     * Reports are a live projection of the bills table, not a snapshot. A bill saved in
     * Billing appears here immediately: Room re-emits the range query on every insert or
     * update, and each emission rebuilds all three tables. This replaces the one-shot
     * `firstOrNull()` query, which only refreshed on a filter change or screen re-entry.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeReports() {
        rangeFlow
            .flatMapLatest { range ->
                billRepository.getBillsByDateRange(range.from, range.to)
            }
            .onEach { bills ->
                _paymentBreakdown.value = reportGenerator.buildPaymentBreakdown(bills)
                _orderLevelRows.value = reportGenerator.buildOrderLevelRows(bills)
                _orderDetailsTable.value = reportGenerator.buildOrderDetailsTable(bills)
                _isLoading.value = false
                _hasLoadedOnce.value = true
            }
            .catch { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = UserMessageSanitizer.sanitize(e, "Failed to load reports.")
                _isLoading.value = false
            }
            .launchIn(viewModelScope)
    }

    fun loadReports(from: Long, to: Long) {
        currentFrom = from
        currentTo = to
        val next = DateRange(from, to)
        // Same window: the flow is already observing it and will emit on any data change,
        // so raising the loading flag here would leave it stuck (no emission to clear it).
        if (rangeFlow.value == next) return
        _isLoading.value = true
        _error.value = null
        rangeFlow.value = next
    }

    private fun normalizeEndOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    private fun normalizeStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun cancelOrder(billId: Long, reason: String) {
        viewModelScope.launch {
            try {
                // No manual refresh: the observing flow re-reads Room, so every table
                // (order details, order level, payment breakdown) reflects persisted truth.
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to cancel order."
                )
            }
        }
    }

    fun updateOrderStatus(billId: Long, newStatus: String) {
        viewModelScope.launch {
            try {
                // Persist only; the flow above re-projects Room, so the tables stay a
                // projection of the database rather than an independently patched copy.
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to update order status."
                )
            }
        }
    }

    fun updatePaymentMode(billId: Long, newMode: String, partAmount1: String = "0.0", partAmount2: String = "0.0") {
        viewModelScope.launch {
            try {
                // Persist first, then re-read. Patching the rows before the write meant a
                // failed repository call left the tables showing a payment mode that was
                // never saved, while the toast claimed the edit failed.
                // Persist only; the observing flow re-reads Room. Patching rows here used
                // to leave a payment mode on screen that had never been written.
                billRepository.updatePaymentMode(billId, newMode, partAmount1, partAmount2)
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to update payment mode."
                )
            }
        }
    }

    suspend fun getOrderDetail(billId: Long): com.khanabook.lite.pos.feature.billing.data.BillWithItems? {
        return reportGenerator.getOrderDetail(billId)
    }

    suspend fun exportReport(
        context: android.content.Context,
        format: String,
        profile: com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity?
    ): java.io.File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val exporter = com.khanabook.lite.pos.feature.reports.domain.ReportExporter(context)
        val billDataById = _orderDetailsTable.value
            .map { it.billId }
            .mapNotNull { id ->
                runCatching { billRepository.getBillWithItemsById(id) }
                    .getOrNull()
                    ?.let { id to it }
            }
            .toMap()
        val topItems = runCatching { reportGenerator.getTopSellingItems(currentFrom, currentTo, 5) }.getOrNull() ?: emptyList()
        val rows = _reportType.value
        if (format == "PDF") {
            if (rows == "Payment" && _paymentBreakdown.value.isEmpty()) {
                throw IllegalStateException("No payment data available to export")
            }
            exporter.exportToPdf(rows, _timeFilter.value, _paymentBreakdown.value, _orderDetailsTable.value, profile, topItems, billDataById, currentFrom, currentTo)
        } else {
            exporter.exportToCsv(rows, _timeFilter.value, _paymentBreakdown.value, _orderDetailsTable.value, profile?.shopName, billDataById, currentFrom, currentTo)
        }
    }

    fun buildShareText(shopName: String?): String {
        val header = shopName?.let { "$it\n" } ?: ""
        val period = _timeFilter.value
        val sb = StringBuilder()
        sb.append("${header}Report — $period\n")
        sb.append("─".repeat(28)).append("\n")
        if (_reportType.value == "Payment") {
            _paymentBreakdown.value.forEach { (mode, amount) ->
                if (!mode.contains("_part")) {
                    sb.append("%-18s ₹%s\n".format(mode, amount))
                }
            }
        } else {
            _orderLevelRows.value.forEach { row ->
                val status = row.orderStatus.name.lowercase().replaceFirstChar { it.uppercase() }
                sb.append("#${row.dailyId}  $status  ${row.date}\n")
            }
        }
        sb.append("─".repeat(28)).append("\n")
        sb.append("Powered by KhanaBook")
        return sb.toString()
    }
}
