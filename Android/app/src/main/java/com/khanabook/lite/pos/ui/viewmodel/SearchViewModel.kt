package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundRequest
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.manager.SearchManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val khanaBookApi: KhanaBookApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val searchManager = SearchManager(billRepository)

    private val _searchResult = MutableStateFlow<BillWithItems?>(null)
    val searchResult: StateFlow<BillWithItems?> = _searchResult

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched

    fun searchByDailyId(displayId: String, date: String) {
        viewModelScope.launch {
            _hasSearched.value = true
            _searchResult.value = searchManager.searchByDailyId(displayId, date)
        }
    }

    fun searchByLifetimeId(id: Long) {
        viewModelScope.launch {
            _hasSearched.value = true
            _searchResult.value = searchManager.searchByLifetimeId(id)
        }
    }

    fun searchByInvoiceNo(invoiceNo: Long) {
        viewModelScope.launch {
            _hasSearched.value = true
            _searchResult.value = searchManager.searchByInvoiceNo(invoiceNo)
        }
    }

    suspend fun getBillsWithPendingKds(): List<BillWithItems> {
        return searchManager.getBillsWithPendingKds()
    }

    fun clearSearch() {
        _searchResult.value = null
        _hasSearched.value = false
    }

    fun updatePaymentMode(billId: Long, newMode: String) {
        viewModelScope.launch {
            billRepository.updatePaymentMode(billId, newMode)
            
            _searchResult.value?.let { current ->
                if (current.bill.id == billId) {
                    _searchResult.value = billRepository.getBillWithItemsById(billId)
                }
            }
        }
    }

    private val _refundResult = MutableStateFlow<RefundResult?>(null)
    val refundResult: StateFlow<RefundResult?> = _refundResult

    data class RefundResult(val success: Boolean, val message: String)

    fun refundBill(billId: Long, amount: String, reason: String) {
        viewModelScope.launch {
            billRepository.refundBill(billId, amount, reason)
            _searchResult.value?.let { current ->
                if (current.bill.id == billId) {
                    _searchResult.value = billRepository.getBillWithItemsById(billId)
                }
            }
            _refundResult.value = RefundResult(true, "Refund recorded successfully")
        }
    }

    fun refundEasebuzz(billId: Long, amount: String, reason: String) {
        viewModelScope.launch {
            try {
                val deviceId = sessionManager.getDeviceId()
                val request = EasebuzzRefundRequest(amount = amount, reason = reason)
                val response = khanaBookApi.refundEasebuzzPayment(deviceId, billId, request)
                if (response.status.equals("success", ignoreCase = true) ||
                    response.status.equals("1", ignoreCase = true)
                ) {
                    billRepository.refundBill(billId, amount, reason)
                    _searchResult.value?.let { current ->
                        if (current.bill.id == billId) {
                            _searchResult.value = billRepository.getBillWithItemsById(billId)
                        }
                    }
                    _refundResult.value = RefundResult(true, "Easebuzz refund initiated: ${response.easebuzzRefundId ?: ""}")
                } else {
                    _refundResult.value = RefundResult(false, response.error ?: "Refund failed")
                }
            } catch (e: Exception) {
                _refundResult.value = RefundResult(false, e.localizedMessage ?: "Refund failed")
            }
        }
    }

    fun clearRefundResult() {
        _refundResult.value = null
    }
}


