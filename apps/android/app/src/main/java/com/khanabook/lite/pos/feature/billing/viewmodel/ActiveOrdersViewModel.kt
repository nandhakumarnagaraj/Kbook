package com.khanabook.lite.pos.feature.billing.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.feature.printing.domain.PrintDispatchMode
import com.khanabook.lite.pos.feature.printing.domain.PrintRouter
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.core.feedback.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val requiresPaymentRecovery: Boolean,
    val singleItemName: String? = null,
    val itemNames: List<String> = emptyList()
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Rows narrowed by [searchQuery]; filtering lives here so it is testable, not in a composable. */
    val filteredRows: StateFlow<List<ActiveOrderSummaryRow>> =
        combine(_rows, _searchQuery) { rows, query -> rows.filter { it.matchesOrderSearch(query) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    init {
        viewModelScope.launch {
            billRepository.getActionableDraftBillsWithItemsFlow().collectLatest { bills ->
                _rows.value = withContext(Dispatchers.Default) {
                    bills.map { detail ->
                        val bill = detail.bill
                        val items = detail.items.filterNot { it.isDeleted }
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
                            hasNewKitchenItems = items.any { !it.sentToKot },
                            requiresPaymentRecovery = detail.payments.any { !it.isDeleted },
                            singleItemName = singleItemName,
                            itemNames = items.map { it.itemName }
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
                    if (result.succeeded > 0) {
                        UiMessage("Bill printed", ToastKind.Success)
                    } else {
                        UiMessage("No bill printer configured", ToastKind.Warning)
                    }
                )
            } catch (e: Exception) {
                Log.e("ActiveOrdersViewModel", "Failed to print bill", e)
                _message.emit(UiMessage("Unable to print bill", ToastKind.Error))
            }
        }
    }
}

/** Matcher for the active order search: order no, invoice, customer, phone, amount, mode, type, items. */
internal fun ActiveOrderSummaryRow.matchesOrderSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return bill.matchesOrderSearch(q) || itemNames.any { it.contains(q, ignoreCase = true) }
}

internal fun BillEntity.matchesOrderSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return dailyOrderDisplay.contains(q, ignoreCase = true) ||
        invoiceNumber?.contains(q, ignoreCase = true) == true ||
        totalAmount.contains(q, ignoreCase = true) ||
        customerWhatsapp?.contains(q, ignoreCase = true) == true ||
        paymentMode.contains(q, ignoreCase = true) ||
        orderType.contains(q, ignoreCase = true) ||
        customerName?.contains(q, ignoreCase = true) == true
}
