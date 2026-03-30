package com.khanabook.lite.pos.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.manager.ReportGenerator
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import com.khanabook.lite.pos.domain.model.OrderLevelRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
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

    private val _reportType = MutableStateFlow("Payment") 
    val reportType: StateFlow<String> = _reportType

    private val _timeFilter = MutableStateFlow("Daily") 
    val timeFilter: StateFlow<String> = _timeFilter

    private val _selectedBillDetails = MutableStateFlow<com.khanabook.lite.pos.data.local.relation.BillWithItems?>(null)
    val selectedBillDetails: StateFlow<com.khanabook.lite.pos.data.local.relation.BillWithItems?> = _selectedBillDetails

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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
            _selectedBillDetails.value = reportGenerator.getOrderDetail(billId)
            _isLoading.value = false
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
            _paymentBreakdown.value = reportGenerator.getPaymentBreakdown(from, to)
            _orderLevelRows.value = reportGenerator.getOrderLevelRows(from, to)
            _orderDetailsTable.value = reportGenerator.getOrderDetailsTable(from, to)
            _isLoading.value = false
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

    fun updateOrderStatus(billId: Long, newStatus: String) {
        viewModelScope.launch {
            billRepository.updateOrderStatus(billId, newStatus)
            if (currentFrom != 0L && currentTo != 0L) {
                loadReports(currentFrom, currentTo)
            }
        }
    }

    fun updatePaymentMode(billId: Long, newMode: String) {
        viewModelScope.launch {
            billRepository.updatePaymentMode(billId, newMode)
            if (currentFrom != 0L && currentTo != 0L) {
                loadReports(currentFrom, currentTo)
            }
        }
    }

    suspend fun getOrderDetail(billId: Long): com.khanabook.lite.pos.data.local.relation.BillWithItems? {
        return reportGenerator.getOrderDetail(billId)
    }
}
