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
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.feedback.UiMessage
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

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

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
                _message.emit(UiMessage("Cannot update KOT for a completed or paid order", ToastKind.Warning))
                return@launch
            }
            if (current.bill.orderStatus == OrderStatus.CANCELLED.dbValue) {
                _message.emit(UiMessage("Cannot update KOT for a cancelled order", ToastKind.Warning))
                return@launch
            }
            if (current.items.none { !it.sentToKot }) {
                _message.emit(UiMessage("No new items to send", ToastKind.Warning))
                return@launch
            }
            _isLoading.value = true
            try {
                val profile = restaurantRepository.getProfile()
                val result = printRouter.printBill(current, profile, PrintDispatchMode.AUTO)
                if (result.succeeded > 0 || result.kitchenQueued) {
                    _message.emit(UiMessage("Updated KOT sent to kitchen", ToastKind.Success))
                } else {
                    _message.emit(UiMessage("Kitchen printer not configured", ToastKind.Warning))
                }
                refresh()
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to update KOT", e)
                _message.emit(UiMessage("Unable to update KOT", ToastKind.Error))
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
                _message.emit(
                    if (result.succeeded > 0) {
                        UiMessage("Bill printed", ToastKind.Success)
                    } else {
                        UiMessage("No bill printer configured", ToastKind.Warning)
                    }
                )
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to print bill", e)
                _message.emit(UiMessage("Unable to print bill", ToastKind.Error))
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
                _message.emit(
                    if (result.succeeded > 0) {
                        UiMessage("KOT reprinted", ToastKind.Success)
                    } else {
                        UiMessage("No kitchen printer configured", ToastKind.Warning)
                    }
                )
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to reprint KOT", e)
                _message.emit(UiMessage("Unable to reprint KOT", ToastKind.Error))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelOrder(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = billRepository.getBillWithItemsById(billId)
            if (current == null) {
                _message.emit(UiMessage("Order not found", ToastKind.Error))
                return@launch
            }
            if (current.bill.orderStatus == OrderStatus.COMPLETED.dbValue || 
                current.bill.paymentStatus == PaymentStatus.SUCCESS.dbValue) {
                _message.emit(UiMessage("Cannot cancel a completed or paid order", ToastKind.Warning))
                return@launch
            }
            if (current.bill.orderStatus == OrderStatus.CANCELLED.dbValue) {
                _message.emit(UiMessage("Order is already cancelled", ToastKind.Warning))
                return@launch
            }
            _isLoading.value = true
            try {
                billRepository.cancelOrder(billId, "Cancelled by cashier")
                _message.emit(UiMessage("Active order cancelled", ToastKind.Success))
                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                Log.e("ActiveOrderDetailVM", "Failed to cancel active order", e)
                _message.emit(UiMessage("Unable to cancel order", ToastKind.Error))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
