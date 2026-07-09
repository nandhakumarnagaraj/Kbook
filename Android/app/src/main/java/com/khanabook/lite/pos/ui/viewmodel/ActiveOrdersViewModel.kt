package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.manager.PrintDispatchMode
import com.khanabook.lite.pos.domain.manager.PrintRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ActiveOrderSummaryRow(
    val bill: BillEntity,
    val itemCount: Int,
    val hasNewKitchenItems: Boolean,
    val singleItemName: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveOrdersViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val restaurantRepository: RestaurantRepository,
    private val printRouter: PrintRouter
) : ViewModel() {
    private val _rows = MutableStateFlow<List<ActiveOrderSummaryRow>>(emptyList())
    val rows: StateFlow<List<ActiveOrderSummaryRow>> = _rows.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            billRepository.getActiveDraftBillsFlow().collectLatest { bills ->
                _rows.value = withContext(Dispatchers.IO) {
                    bills.map { bill ->
                        val detail = billRepository.getBillWithItemsById(bill.id)
                        val items = detail?.items ?: emptyList()
                        val qty = items.sumOf { it.quantity }
                        val singleItemName = if (qty == 1) {
                            val singleItem = items.firstOrNull { it.quantity > 0 }
                            if (singleItem != null) {
                                val variantStr = if (!singleItem.variantName.isNullOrBlank()) " (${singleItem.variantName})" else ""
                                "${singleItem.itemName}$variantStr"
                            } else null
                        } else null
                        ActiveOrderSummaryRow(
                            bill = bill,
                            itemCount = qty,
                            hasNewKitchenItems = detail?.items?.any { !it.sentToKot } == true,
                            singleItemName = singleItemName
                        )
                    }
                }
            }
        }
    }

    fun printBill(billId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bill = billRepository.getBillWithItemsById(billId) ?: return@launch
                val profile = restaurantRepository.getProfile()
                val result = printRouter.printBill(bill, profile, PrintDispatchMode.MANUAL_RECEIPT_ONLY)
                _message.emit(
                    if (result.succeeded > 0) "Bill printed" else "No bill printer configured"
                )
            } catch (e: Exception) {
                Log.e("ActiveOrdersViewModel", "Failed to print bill", e)
                _message.emit("Unable to print bill")
            }
        }
    }
}
