package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyClosingData(
    val date: String = "",
    val totalOrders: Int = 0,
    val completedOrders: Int = 0,
    val cancelledOrders: Int = 0,
    val draftOrders: Int = 0,
    val totalRevenue: Double = 0.0,
    val refundedAmount: Double = 0.0,
    val netRevenue: Double = 0.0,
    val paymentSplits: List<PaymentSplit> = emptyList(),
    val expectedCash: Double = 0.0
)

data class PaymentSplit(
    val mode: String,
    val label: String,
    val count: Int,
    val total: Double
)

@HiltViewModel
class DailyClosingViewModel @Inject constructor(
    private val billRepository: BillRepository
) : ViewModel() {

    private val _data = MutableStateFlow<DailyClosingData?>(null)
    val data: StateFlow<DailyClosingData?> = _data

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val today = getTodayRange()
                val bills = billRepository.getBillsByDateRange(today.first, today.second).first()
                _data.value = computeClosing(bills, formatDate(today.first))
            } catch (e: Exception) {
                _error.value = "Failed to load: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun computeClosing(bills: List<BillEntity>, date: String): DailyClosingData {
        val completed = bills.filter {
            it.orderStatus.equals("completed", ignoreCase = true) ||
            it.paymentStatus.equals("paid", ignoreCase = true) ||
            it.paymentStatus.equals("success", ignoreCase = true)
        }
        val cancelled = bills.filter {
            it.orderStatus.equals("cancelled", ignoreCase = true)
        }
        val draft = bills.filter {
            it.orderStatus.equals("draft", ignoreCase = true)
        }

        val totalRevenue = completed.sumOf { it.totalAmount.toDoubleOrNull() ?: 0.0 }
        val refundedAmount = bills.sumOf { it.refundAmount?.toDoubleOrNull() ?: 0.0 }
        val netRevenue = totalRevenue - refundedAmount

        // Payment mode breakdown
        val modeMap = mutableMapOf<String, Pair<Int, Double>>()
        for (bill in completed) {
            val mode = bill.paymentMode.lowercase()
            val existing = modeMap[mode] ?: Pair(0, 0.0)
            modeMap[mode] = Pair(existing.first + 1, existing.second + (bill.totalAmount.toDoubleOrNull() ?: 0.0))
        }

        val paymentSplits = modeMap.entries.map { (mode, data) ->
            PaymentSplit(
                mode = mode,
                label = getModeLabel(mode),
                count = data.first,
                total = data.second
            )
        }.sortedByDescending { it.total }

        val expectedCash = paymentSplits
            .filter { it.mode.contains("cash") }
            .sumOf { it.total }

        return DailyClosingData(
            date = date,
            totalOrders = bills.size,
            completedOrders = completed.size,
            cancelledOrders = cancelled.size,
            draftOrders = draft.size,
            totalRevenue = totalRevenue,
            refundedAmount = refundedAmount,
            netRevenue = netRevenue,
            paymentSplits = paymentSplits,
            expectedCash = expectedCash
        )
    }

    private fun getModeLabel(mode: String): String = when {
        mode.contains("cash") && mode.contains("upi") -> "Cash + UPI Split"
        mode.contains("cash") && mode.contains("pos") -> "Cash + POS Split"
        mode.contains("upi") && mode.contains("pos") -> "UPI + POS Split"
        mode == "cash" -> "Cash"
        mode == "upi" -> "UPI"
        mode == "pos" || mode == "card" -> "POS / Card"
        else -> mode.replaceFirstChar { it.uppercase() }
    }

    private fun getTodayRange(): Pair<Long, Long> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    private fun formatDate(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(ms))
    }
}
