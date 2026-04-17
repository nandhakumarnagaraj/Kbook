package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.manager.ReportGenerator
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import com.khanabook.lite.pos.domain.model.OrderLevelRow
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val billRepository: BillRepository
) : ViewModel() {

    private val reportGenerator = ReportGenerator(billRepository)

    private val _paymentBreakdown = MutableStateFlow<Map<String, String>>(emptyMap())
    val paymentBreakdown: StateFlow<Map<String, String>> = _paymentBreakdown

    private val _orderLevelRows = MutableStateFlow<List<OrderLevelRow>>(emptyList())
    val orderLevelRows: StateFlow<List<OrderLevelRow>> = _orderLevelRows

    private val _orderDetailsTable = MutableStateFlow<List<OrderDetailRow>>(emptyList())
    val orderDetailsTable: StateFlow<List<OrderDetailRow>> = _orderDetailsTable

    private val _reportType = MutableStateFlow("Order") 
    val reportType: StateFlow<String> = _reportType

    private val _timeFilter = MutableStateFlow("Daily") 
    val timeFilter: StateFlow<String> = _timeFilter

    private val _selectedBillDetails = MutableStateFlow<com.khanabook.lite.pos.data.local.relation.BillWithItems?>(null)
    val selectedBillDetails: StateFlow<com.khanabook.lite.pos.data.local.relation.BillWithItems?> = _selectedBillDetails

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentFrom: Long = System.currentTimeMillis() - 86400000L
    private var currentTo: Long = System.currentTimeMillis()

    fun setReportType(type: String) {
        _reportType.value = type
    }

    fun setTimeFilter(filter: String) {
        _timeFilter.value = filter
        updateDateRangeAndLoad(filter)
    }

    fun loadBillDetails(billId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _selectedBillDetails.value = reportGenerator.getOrderDetail(billId)
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to load order details."
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearBillDetails() {
        _selectedBillDetails.value = null
    }

    private fun updateDateRangeAndLoad(filter: String) {
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis

        val from = when (filter) {
            "Daily" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "Weekly" -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "Monthly" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            else -> cal.timeInMillis
        }
        loadReports(from, to)
    }

    fun setCustomDateRange(from: Long, to: Long) {
        _timeFilter.value = "Custom"
        loadReports(from, normalizeEndOfDay(to))
    }

    fun loadReports(from: Long, to: Long) {
        currentFrom = from
        currentTo = to
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _paymentBreakdown.value = reportGenerator.getPaymentBreakdown(from, to)
                _orderLevelRows.value = reportGenerator.getOrderLevelRows(from, to)
                _orderDetailsTable.value = reportGenerator.getOrderDetailsTable(from, to)
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to load reports."
                )
            } finally {
                _isLoading.value = false
            }
        }
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

    fun cancelOrder(billId: Long, reason: String) {
        viewModelScope.launch {
            try {
                billRepository.cancelOrder(billId, reason)
                // Update in-place — avoids full reload flicker
                _orderDetailsTable.update { rows ->
                    rows.map { row ->
                        if (row.billId == billId)
                            row.copy(
                                orderStatus = com.khanabook.lite.pos.domain.model.OrderStatus.CANCELLED,
                                currentStatus = "cancelled",
                                cancelReason = reason
                            )
                        else row
                    }
                }
                _orderLevelRows.update { rows ->
                    rows.map { row ->
                        if (row.billId == billId)
                            row.copy(
                                orderStatus = com.khanabook.lite.pos.domain.model.OrderStatus.CANCELLED,
                                cancelReason = reason
                            )
                        else row
                    }
                }
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
                billRepository.updateOrderStatus(billId, newStatus)
                if (currentFrom != 0L && currentTo != 0L) {
                    loadReports(currentFrom, currentTo)
                }
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
                billRepository.updatePaymentMode(billId, newMode, partAmount1, partAmount2)
                if (currentFrom != 0L && currentTo != 0L) {
                    loadReports(currentFrom, currentTo)
                }
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(
                    e,
                    "Failed to update payment mode."
                )
            }
        }
    }

    suspend fun getOrderDetail(billId: Long): com.khanabook.lite.pos.data.local.relation.BillWithItems? {
        return reportGenerator.getOrderDetail(billId)
    }

    suspend fun exportReport(context: android.content.Context, format: String, shopName: String?): java.io.File {
        val exporter = com.khanabook.lite.pos.domain.manager.ReportExporter(context)
        val billDataById = _orderDetailsTable.value
            .map { it.billId }
            .mapNotNull { id -> billRepository.getBillWithItemsById(id)?.let { id to it } }
            .toMap()
        return if (format == "PDF") {
            exporter.exportToPdf(_reportType.value, _timeFilter.value, _paymentBreakdown.value, _orderDetailsTable.value, shopName, billDataById)
        } else {
            exporter.exportToCsv(_reportType.value, _timeFilter.value, _paymentBreakdown.value, _orderDetailsTable.value, shopName, billDataById)
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
