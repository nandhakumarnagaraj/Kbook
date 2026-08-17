package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PaymentLinkState {
    object Idle : PaymentLinkState()
    object Loading : PaymentLinkState()
    data class Success(val linkUrl: String, val merchantTxn: String) : PaymentLinkState()
    data class Error(val error: String) : PaymentLinkState()
}

@HiltViewModel
class PaymentLinkViewModel @Inject constructor(
    private val paymentRepository: EasebuzzPaymentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<PaymentLinkState>(PaymentLinkState.Idle)
    val state: StateFlow<PaymentLinkState> = _state.asStateFlow()

    val restaurantId: Long = savedStateHandle["restaurantId"] ?: 0L

    fun createLink(
        customerName: String,
        customerEmail: String,
        customerPhone: String,
        amount: String,
        message: String,
        merchantTxn: String
    ) {
        _state.value = PaymentLinkState.Loading
        viewModelScope.launch {
            paymentRepository.createPaymentLink(
                restaurantId = restaurantId,
                amount = amount,
                customerName = customerName,
                customerEmail = customerEmail,
                customerPhone = customerPhone,
                message = message,
                merchantTxn = merchantTxn
            ).onSuccess { result ->
                val status = result["status"]?.toString() ?: "failure"
                if (status == "success") {
                    val linkUrl = result["payment_url"]?.toString() ?: ""
                    val merchantTxn = result["merchant_txn"]?.toString() ?: ""
                    _state.value = PaymentLinkState.Success(linkUrl, merchantTxn)
                } else {
                    val error = result["error"]?.toString() ?: "Failed to create payment link"
                    _state.value = PaymentLinkState.Error(error)
                }
            }.onFailure { e ->
                _state.value = PaymentLinkState.Error(e.message ?: "Network error")
            }
        }
    }

    fun reset() {
        _state.value = PaymentLinkState.Idle
    }
}