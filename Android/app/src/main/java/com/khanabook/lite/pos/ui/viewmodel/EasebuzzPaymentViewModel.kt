package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderResponse
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EasebuzzPaymentState {
    object Idle : EasebuzzPaymentState()
    object CreatingOrder : EasebuzzPaymentState()
    data class PaymentReady(
        val paymentUrl: String,
        val accessToken: String,
        val txnId: String
    ) : EasebuzzPaymentState()
    object Verifying : EasebuzzPaymentState()
    data class PaymentSuccess(val txnId: String) : EasebuzzPaymentState()
    data class PaymentFailed(val message: String) : EasebuzzPaymentState()
    data class Error(val message: String) : EasebuzzPaymentState()
}

@HiltViewModel
class EasebuzzPaymentViewModel @Inject constructor(
    private val paymentRepository: EasebuzzPaymentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<EasebuzzPaymentState>(EasebuzzPaymentState.Idle)
    val state: StateFlow<EasebuzzPaymentState> = _state.asStateFlow()

    val billId: Long = savedStateHandle["billId"] ?: 0L
    val restaurantId: Long = savedStateHandle["restaurantId"] ?: 0L
    var currentTxnId: String = ""
        private set

    fun createOrder() {
        if (_state.value is EasebuzzPaymentState.CreatingOrder ||
            _state.value is EasebuzzPaymentState.PaymentReady ||
            _state.value is EasebuzzPaymentState.Verifying) return // Prevent double-tap
        if (billId == 0L || restaurantId == 0L) {
            _state.value = EasebuzzPaymentState.Error("Invalid bill or restaurant ID")
            return
        }
        viewModelScope.launch {
            _state.value = EasebuzzPaymentState.CreatingOrder
            paymentRepository.createOrder(billId, restaurantId)
                .onSuccess { response ->
                    if (response.status == "success" && response.paymentUrl != null && response.accessToken != null) {
                        currentTxnId = response.txnId ?: ""
                        _state.value = EasebuzzPaymentState.PaymentReady(
                            paymentUrl = response.paymentUrl,
                            accessToken = response.accessToken,
                            txnId = response.txnId ?: ""
                        )
                    } else {
                        val errorMsg = response.error ?: "Failed to create payment order"
                        _state.value = EasebuzzPaymentState.Error(errorMsg)
                        KhanaToast.show(errorMsg, ToastKind.Error)
                    }
                }
                .onFailure { e ->
                    val msg = "Unable to start payment. Please check your internet connection."
                    _state.value = EasebuzzPaymentState.Error(msg)
                    KhanaToast.show(msg, ToastKind.Error)
                }
        }
    }

    fun onPaymentReturn(success: Boolean) {
        if (success) {
            verifyPayment()
        } else {
            _state.value = EasebuzzPaymentState.PaymentFailed("Payment was cancelled or failed")
        }
    }

    fun verifyPayment() {
        viewModelScope.launch {
            _state.value = EasebuzzPaymentState.Verifying
            paymentRepository.verifyPayment(billId)
                .onSuccess { result ->
                    val status = result["paymentStatus"]?.toString()
                        ?: result["status"]?.toString()
                    if (status == "paid" || status == "success") {
                        val txnId = result["gatewayTxnId"]?.toString()
                            ?: result["txnid"]?.toString() ?: ""
                        _state.value = EasebuzzPaymentState.PaymentSuccess(txnId)
                        KhanaToast.show("Payment successful!", ToastKind.Success)
                    } else {
                        _state.value = EasebuzzPaymentState.PaymentFailed(
                            "Payment status: ${status ?: "unknown"}"
                        )
                    }
                }
                .onFailure { e ->
                    _state.value = EasebuzzPaymentState.Error(
                        e.message ?: "Failed to verify payment"
                    )
                }
        }
    }

    fun checkStatus() {
        viewModelScope.launch {
            paymentRepository.getPaymentStatus(billId, refresh = true)
                .onSuccess { result ->
                    val status = result["paymentStatus"]?.toString()
                        ?: result["status"]?.toString()
                    if (status == "paid" || status == "success") {
                        val txnId = result["gatewayTxnId"]?.toString() ?: ""
                        _state.value = EasebuzzPaymentState.PaymentSuccess(txnId)
                        KhanaToast.show("Payment confirmed!", ToastKind.Success)
                    } else {
                        KhanaToast.show("Status: ${status ?: "pending"}", ToastKind.Info)
                    }
                }
                .onFailure { e ->
                    KhanaToast.show(e.message ?: "Failed to check status", ToastKind.Error)
                }
        }
    }

    fun reset() {
        _state.value = EasebuzzPaymentState.Idle
    }

    fun onSdkUnavailable(message: String) {
        _state.value = EasebuzzPaymentState.Error(message)
    }
}
