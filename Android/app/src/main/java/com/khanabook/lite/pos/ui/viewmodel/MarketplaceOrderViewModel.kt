package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.dto.MarketplaceOrderCounts
import com.khanabook.lite.pos.data.remote.dto.MarketplaceOrderDto
import com.khanabook.lite.pos.data.repository.MarketplaceOrderRepository
import com.khanabook.lite.pos.domain.util.NetworkMonitor
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MarketplaceOrdersUiState {
    object Loading : MarketplaceOrdersUiState()
    data class Online(val orders: List<MarketplaceOrderDto>, val counts: MarketplaceOrderCounts) : MarketplaceOrdersUiState()
    data class Error(val message: String) : MarketplaceOrdersUiState()
}

@HiltViewModel
class MarketplaceOrderViewModel @Inject constructor(
    private val orderRepository: MarketplaceOrderRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow<MarketplaceOrdersUiState>(MarketplaceOrdersUiState.Loading)
    val uiState: StateFlow<MarketplaceOrdersUiState> = _uiState.asStateFlow()

    private val _isOffline = networkMonitor.status
        .map { it == com.khanabook.lite.pos.domain.util.ConnectionStatus.Unavailable }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isOffline: StateFlow<Boolean> = _isOffline

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _isOffline.collect { offline ->
                if (offline) {
                    _uiState.value = MarketplaceOrdersUiState.Error(
                        "No internet connection. Marketplace orders require a live connection."
                    )
                } else {
                    loadOnline()
                }
            }
        }
    }

    private suspend fun loadOnline() {
        try {
            val pending = orderRepository.counts()
                .getOrElse { _uiState.value = MarketplaceOrdersUiState.Error(it.message ?: "Failed to load counts"); return }
            val orders = orderRepository.list("pending").firstOrNull().orEmpty()
            _uiState.value = MarketplaceOrdersUiState.Online(orders, pending)
        } catch (e: Exception) {
            _uiState.value = MarketplaceOrdersUiState.Error(e.message ?: "Failed to load marketplace orders")
        }
    }

    fun accept(orderId: Long) = action { orderRepository.accept(orderId) }
    fun reject(orderId: Long, reason: String) = action { orderRepository.reject(orderId, reason) }
    fun markReady(orderId: Long) = action { orderRepository.markReady(orderId) }
    fun complete(orderId: Long) = action { orderRepository.complete(orderId) }

    private fun action(call: suspend () -> Result<*>) {
        viewModelScope.launch {
            call().onSuccess { loadOnline() }.onFailure { e ->
                KhanaToast.show(e.message ?: "Action failed", ToastKind.Error)
            }
        }
    }
}
