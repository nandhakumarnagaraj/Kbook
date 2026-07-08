package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.manager.PrintDispatchMode
import com.khanabook.lite.pos.domain.manager.PrintRouter
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ActiveOrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val billRepository: BillRepository,
    private val restaurantRepository: RestaurantRepository,
    private val printRouter: PrintRouter
) : ViewModel() {
    private val billId: Long = checkNotNull(savedStateHandle["billId"])

    private val _bill = MutableStateFlow<BillWithItems?>(null)
    val bill: StateFlow<BillWithItems?> = _bill.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _bill.value = billRepository.getBillWithItemsById(billId)
        }
    }

    fun updateKot() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = billRepository.getBillWithItemsById(billId) ?: return@launch
            if (current.bill.orderStatus == OrderStatus.COMPLETED.dbValue || 
                current.bill.paymentStatus == PaymentStatus.SUCCESS.dbValue) {
                _message.emit("Cannot update KOT for a completed or paid order")
                return@launch
            }
            if (current.bill.orderStatus == OrderStatus.CANCELLED.dbValue) {
                _message.emit("Cannot update KOT for a cancelled order")
                return@launch
            }
            if (current.items.none { !it.sentToKot }) {
                _message.emit("No new items to send")
                return@launch
            }
            _isLoading.value = true
            try {
                val profile = restaurantRepository.getProfile()
                val result = printRouter.printBill(current, profile, PrintDispatchMode.AUTO)
                if (result.succeeded > 0 || result.kitchenQueued) {
                    _message.emit("Updated KOT sent to kitchen")
                } else {
                    _message.emit("Kitchen printer not configured")
                }
                refresh()
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to update KOT", e)
                _message.emit("Unable to update KOT")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun printBill() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = billRepository.getBillWithItemsById(billId) ?: return@launch
            _isLoading.value = true
            try {
                val profile = restaurantRepository.getProfile()
                val result = printRouter.printBill(current, profile, PrintDispatchMode.MANUAL_RECEIPT_ONLY)
                _message.emit(if (result.succeeded > 0) "Bill printed" else "No bill printer configured")
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to print bill", e)
                _message.emit("Unable to print bill")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun reprintKot() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = billRepository.getBillWithItemsById(billId) ?: return@launch
            _isLoading.value = true
            try {
                val profile = restaurantRepository.getProfile()
                val result = printRouter.printBill(current, profile, PrintDispatchMode.MANUAL_KITCHEN_ONLY)
                _message.emit(if (result.succeeded > 0) "KOT reprinted" else "No kitchen printer configured")
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to reprint KOT", e)
                _message.emit("Unable to reprint KOT")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelOrder(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = billRepository.getBillWithItemsById(billId)
            if (current == null) {
                _message.emit("Order not found")
                return@launch
            }
            if (current.bill.orderStatus == OrderStatus.COMPLETED.dbValue || 
                current.bill.paymentStatus == PaymentStatus.SUCCESS.dbValue) {
                _message.emit("Cannot cancel a completed or paid order")
                return@launch
            }
            if (current.bill.orderStatus == OrderStatus.CANCELLED.dbValue) {
                _message.emit("Order is already cancelled")
                return@launch
            }
            _isLoading.value = true
            try {
                billRepository.cancelOrder(billId, "Cancelled by cashier")
                _message.emit("Active order cancelled")
                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to cancel active order", e)
                _message.emit("Unable to cancel order")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
