package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.manager.SearchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val billRepository: BillRepository
) : ViewModel() {

    private val searchManager = SearchManager(billRepository)

    private val _searchResult = MutableStateFlow<BillWithItems?>(null)
    val searchResult: StateFlow<BillWithItems?> = _searchResult

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched

    suspend fun searchByDailyId(displayId: String, date: String): BillWithItems? {
        return searchManager.searchByDailyId(displayId, date)
    }

    suspend fun searchByLifetimeId(id: Long): BillWithItems? {
        return searchManager.searchByLifetimeId(id)
    }

    suspend fun searchByInvoiceNo(invoiceNo: Long): BillWithItems? {
        return searchManager.searchByInvoiceNo(invoiceNo)
    }

    fun publishSearchResult(result: BillWithItems?) {
        _hasSearched.value = true
        _searchResult.value = result
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
}


