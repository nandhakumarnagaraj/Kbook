package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_CREATE_ATTEMPTS = 5
private const val MAX_STATUS_POLLS = 10
private const val MAX_BACKGROUND_POLLS = 100
private const val SESSION_SECONDS = 300

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

    private val _secondsLeft = MutableStateFlow(SESSION_SECONDS)
    val secondsLeft: StateFlow<Int> = _secondsLeft.asStateFlow()

    private var pollJob: Job? = null
    private var countdownJob: Job? = null

    val billId: Long = savedStateHandle["billId"] ?: 0L
    val restaurantId: Long = savedStateHandle["restaurantId"] ?: 0L
    var currentTxnId: String = ""
        private set
    var lastPaymentUrl: String? = null
        private set

    override fun onCleared() {
        pollJob?.cancel()
        countdownJob?.cancel()
        super.onCleared()
    }

    fun createOrder() {
        if (_state.value is EasebuzzPaymentState.CreatingOrder ||
            _state.value is EasebuzzPaymentState.PaymentReady ||
            _state.value is EasebuzzPaymentState.Verifying
        ) return
        if (billId == 0L || restaurantId == 0L) {
            _state.value = EasebuzzPaymentState.Error("Invalid bill or restaurant ID")
            return
        }
        viewModelScope.launch {
            var lastError: Throwable? = null
            for (attempt in 0..MAX_CREATE_ATTEMPTS) {
                _state.value = EasebuzzPaymentState.CreatingOrder
                var orderReady = false
                paymentRepository.createOrder(billId, restaurantId)
                    .onSuccess { response ->
                        if (response.status == "success" && response.paymentUrl != null && response.accessToken != null) {
                            currentTxnId = response.txnId ?: ""
                            lastPaymentUrl = response.paymentUrl
                            _state.value = EasebuzzPaymentState.PaymentReady(
                                paymentUrl = response.paymentUrl,
                                accessToken = response.accessToken,
                                txnId = response.txnId ?: ""
                            )
                            orderReady = true
                        } else {
                            lastError = IllegalStateException(
                                response.error ?: "Failed to create payment order"
                            )
                        }
                    }
                    .onFailure { e -> lastError = e }
                if (orderReady) {
                    startSessionTimers()
                    return@launch
                }
                if (attempt < MAX_CREATE_ATTEMPTS) {
                    val backoffMs = (1000L shl attempt).coerceAtMost(16000L)
                    delay(backoffMs)
                }
            }
            _state.value = EasebuzzPaymentState.Error(
                lastError?.message ?: "Unable to start payment. Please check your internet connection."
            )
            KhanaToast.show(
                lastError?.message ?: "Unable to start payment. Please check your internet connection.",
                ToastKind.Error
            )
        }
    }

    fun verifyAndComplete(txnIdFromSdk: String? = null) {
        if (_state.value is EasebuzzPaymentState.Verifying) return
        pollJob?.cancel()
        countdownJob?.cancel()
        viewModelScope.launch {
            _state.value = EasebuzzPaymentState.Verifying
            var paid = false
            var pollAttempts = 0
            while (pollAttempts < MAX_STATUS_POLLS) {
                paymentRepository.getPaymentStatus(billId, refresh = true)
                    .onSuccess { result ->
                        val status = result["paymentStatus"]?.toString()
                            ?: result["status"]?.toString()
                        if (status == "paid" || status == "success") paid = true
                    }
                pollAttempts++
                if (paid) break
                if (pollAttempts < MAX_STATUS_POLLS) delay(3000)
            }
            paymentRepository.verifyPayment(billId)
                .onSuccess { result ->
                    val status = result["paymentStatus"]?.toString()
                        ?: result["status"]?.toString()
                    if (status == "paid" || status == "success") {
                        val txnId = result["gatewayTxnId"]?.toString()
                            ?: result["txnid"]?.toString()
                            ?: txnIdFromSdk ?: currentTxnId
                        _state.value = EasebuzzPaymentState.PaymentSuccess(txnId)
                        KhanaToast.show("Payment successful!", ToastKind.Success)
                    } else {
                        _state.value = if (paid) {
                            EasebuzzPaymentState.PaymentSuccess(txnIdFromSdk ?: currentTxnId)
                        } else {
                            EasebuzzPaymentState.PaymentFailed("Payment verification failed")
                        }
                    }
                }
                .onFailure { e ->
                    _state.value = if (paid) {
                        EasebuzzPaymentState.PaymentSuccess(txnIdFromSdk ?: currentTxnId)
                    } else {
                        EasebuzzPaymentState.PaymentFailed(
                            e.message ?: "Payment verification failed"
                        )
                    }
                }
        }
    }

    fun verifyPayment() {
        viewModelScope.launch {
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
                        KhanaToast.show("Status: ${status ?: "pending"}", ToastKind.Info)
                    }
                }
                .onFailure { e ->
                    KhanaToast.show(e.message ?: "Failed to check status", ToastKind.Error)
                }
        }
    }

    fun retry() {
        pollJob?.cancel()
        countdownJob?.cancel()
        _secondsLeft.value = SESSION_SECONDS
        _state.value = EasebuzzPaymentState.Idle
        createOrder()
    }

    fun reset() {
        pollJob?.cancel()
        countdownJob?.cancel()
        _secondsLeft.value = SESSION_SECONDS
        _state.value = EasebuzzPaymentState.Idle
    }

    fun onSdkUnavailable(message: String) {
        _state.value = EasebuzzPaymentState.Error(message)
    }

    private fun startSessionTimers() {
        pollJob?.cancel()
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _secondsLeft.value = SESSION_SECONDS
            while (_secondsLeft.value > 0) {
                delay(1000)
                _secondsLeft.value--
            }
            if (_state.value is EasebuzzPaymentState.PaymentReady) {
                _state.value = EasebuzzPaymentState.PaymentFailed("Payment session expired")
            }
        }
        pollJob = viewModelScope.launch {
            var pollAttempts = 0
            while (pollAttempts < MAX_BACKGROUND_POLLS) {
                delay(3000)
                if (_state.value !is EasebuzzPaymentState.PaymentReady) break
                paymentRepository.getPaymentStatus(billId, refresh = true)
                    .onSuccess { result ->
                        val status = result["paymentStatus"]?.toString()
                            ?: result["status"]?.toString()
                        if (status == "paid" || status == "success") {
                            verifyAndComplete()
                        }
                    }
                pollAttempts++
            }
        }
    }
}