package com.khanabook.lite.pos.feature.reports.viewmodel
import com.khanabook.lite.pos.core.util.BusinessDate
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.reports.domain.ReportGenerator
import com.khanabook.lite.pos.feature.reports.domain.OrderDetailRow
import com.khanabook.lite.pos.feature.reports.domain.OrderLevelRow
import com.khanabook.lite.pos.core.util.AppConstants
import com.khanabook.lite.pos.core.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val restaurantRepository: RestaurantRepository,
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

    /**
     * [canViewFullReports] as an observable value.
     *
     * A permission sync can grant `reports.full` to an already signed-in staff user, and the
     * Orders filter row must widen when that happens. Calling `canViewFullReports()` directly
     * from composition (or caching it with `remember`) never observes that update, so the row
     * stayed at `Today`-only until the screen was recreated. Seeded with the current answer and
     * recomputed whenever the granted set changes.
     */
    val canViewFullReportsFlow: StateFlow<Boolean> =
        permissionManager.grantedPermissions
            .map { canViewFullReports() }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, canViewFullReports())

    private val reportGenerator = ReportGenerator(billRepository)

    private val _paymentBreakdown = MutableStateFlow<Map<String, String>>(emptyMap())
    val paymentBreakdown: StateFlow<Map<String, String>> = _paymentBreakdown

    private val _orderLevelRows = MutableStateFlow<List<OrderLevelRow>>(emptyList())
    val orderLevelRows: StateFlow<List<OrderLevelRow>> = _orderLevelRows

    private val _orderDetailsTable = MutableStateFlow<List<OrderDetailRow>>(emptyList())
    val orderDetailsTable: StateFlow<List<OrderDetailRow>> = _orderDetailsTable

    private val _timeFilter = MutableStateFlow("Today") 
    val timeFilter: StateFlow<String> = _timeFilter

    /** Dates last chosen in the Custom picker, shown on the Custom chip. */
    private val _customRangeLabel = MutableStateFlow<String?>(null)
    val customRangeLabel: StateFlow<String?> = _customRangeLabel

    /**
     * `d MMM` label for the business date each preset chip resolves to, e.g.
     * `Today -> 14 Nov`, `Yesterday -> 13 Nov`. The selected chip shows this instead of the
     * word, so the row states the day the query actually covers.
     */
    private val _dateFilterLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val dateFilterLabels: StateFlow<Map<String, String>> = _dateFilterLabels

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
    private var currentProfile: RestaurantProfileEntity? = null

    /** Calendar dates the user picked in the Custom dialog, if any.
     *
     * Stored as dates, not millis, so a closing-time change can re-derive the window instead
     * of pinning the instants that the old boundary produced. */
    private var customFromDate: LocalDate? = null
    private var customToDate: LocalDate? = null

    /** Window currently observed; Room re-emits it whenever the bills table changes.
     *
     * Declared above [init] on purpose: Kotlin runs initializers and init blocks in
     * declaration order, and the init block calls [loadReports] which reads this field.
     * Placed below the init block it was still null at construction time. */
    private val rangeFlow = MutableStateFlow(DateRange(0L, 0L))

    init {
        // Load the opening range once here. Doing it from OrdersScreen via
        // LaunchedEffect(Unit) re-ran on every tab re-entry and silently reset the
        // user's chosen filter back to "Today".
        observeReports()
        viewModelScope.launch {
            restaurantRepository.getProfileFlow().collect { profile ->
                currentProfile = profile
                updateDateRangeAndLoad(_timeFilter.value)
            }
        }
        updateDateRangeAndLoad("Today")
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

    /**
     * Re-reads the open dialog's bill after a write. The details dialog holds a snapshot, not an
     * observed query, so without this it kept showing the old payment mode (or the live actions
     * on a bill that had just been cancelled) until it was closed and reopened.
     */
    private suspend fun refreshOpenBill(billId: Long) {
        if (_selectedBillDetails.value?.bill?.id != billId) return
        runCatching { reportGenerator.getOrderDetail(billId) }
            .onSuccess { _selectedBillDetails.value = it }
    }

    fun clearError() {
        _error.value = null
    }

    private fun updateDateRangeAndLoad(filter: String) {
        refreshDateFilterLabels()
        // Custom keeps the dates the user picked, but the window is re-derived from the
        // CURRENT profile on every call. Caching the instants instead would leave the list
        // (and the caption) on the old boundary after a closing-time edit, because this
        // method also runs whenever the saved profile re-emits.
        if (filter == "Custom") {
            val fromDate = customFromDate
            val toDate = customToDate
            if (fromDate != null && toDate != null) {
                val profile = currentProfile
                loadReports(
                    BusinessDate.windowStart(fromDate, profile),
                    BusinessDate.windowEnd(toDate, profile) - 1
                )
                return
            }
        }

        val profile = currentProfile
        val todayStr = BusinessDate.businessDate(System.currentTimeMillis(), profile)
        val today = LocalDate.parse(todayStr)

        val (from, to) = when (filter) {
            "Yesterday" -> {
                val yesterday = today.minusDays(1)
                val from = BusinessDate.windowStart(yesterday, profile)
                val to = BusinessDate.windowEnd(yesterday, profile) - 1
                from to to
            }
            else -> {
                // "Today" (legacy "Daily" treated the same).
                // Uses the restaurant's business day window (accounting for closing time
                // and settlement buffer), so midnight does not prematurely split the active shift.
                val from = BusinessDate.windowStart(today, profile)
                val to = BusinessDate.windowEnd(today, profile) - 1
                from to to
            }
        }
        loadReports(from, to)
    }

    /**
     * Resolves the Today/Yesterday labels from the same business date the queries use, so the
     * chip cannot disagree with the rows. Runs on every filter change, on resume, and when the
     * profile re-emits, which is also what rolls the labels over at closing time.
     */
    private fun refreshDateFilterLabels() {
        val today = LocalDate.parse(
            BusinessDate.businessDate(System.currentTimeMillis(), currentProfile)
        )
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
        _dateFilterLabels.value = mapOf(
            "Today" to today.format(formatter),
            "Yesterday" to today.minusDays(1).format(formatter)
        )
    }

    /**
     * Recomputes the current filter's window. Called when the screen resumes so "Today"
     * rolls over when the business day closes and a re-opened screen never keeps a stale day window.
     */
    fun refreshCurrentFilter() {
        updateDateRangeAndLoad(_timeFilter.value)
    }

    fun setCustomDateRange(from: Long, to: Long) {
        _timeFilter.value = "Custom"
        val zone = BusinessDate.zoneOf(currentProfile?.timezone)
        val fromDate = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val toDate = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        customFromDate = fromDate
        customToDate = toDate
        // The chip only shows the range while Custom is selected, so storing it here cannot
        // leak into Today/Yesterday. Picked calendar dates, not the business-window instants:
        // the chip echoes the user's own selection, which is all the screen states about the
        // range now that the business-window caption is gone.
        _customRangeLabel.value = formatCustomRange(fromDate, toDate)
        // One path for windows: also used when the profile re-emits after a closing-time edit.
        updateDateRangeAndLoad("Custom")
    }

    /** Short `d MMM` label for the Custom chip, e.g. `12 Nov` or `10 Nov – 12 Nov`. */
    private fun formatCustomRange(fromDate: LocalDate, toDate: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
        return if (fromDate == toDate) {
            fromDate.format(formatter)
        } else {
            "${fromDate.format(formatter)} – ${toDate.format(formatter)}"
        }
    }

    private data class DateRange(val from: Long, val to: Long)

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

    private fun loadReports(from: Long, to: Long) {
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

    fun cancelOrder(billId: Long, reason: String) {
        viewModelScope.launch {
            try {
                // No manual refresh: the observing flow re-reads Room, so every table
                // (order details, order level, payment breakdown) reflects persisted truth.
                billRepository.cancelOrder(billId, reason)
                refreshOpenBill(billId)
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to cancel order."
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
                refreshOpenBill(billId)
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

}
