package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.dto.MerchantCustomerOrderDetailResponse
import com.khanabook.lite.pos.data.remote.dto.MerchantCustomerOrderSummaryResponse
import com.khanabook.lite.pos.data.repository.StorefrontOrderRepository
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorefrontOrdersViewModel @Inject constructor(
    private val storefrontOrderRepository: StorefrontOrderRepository
) : ViewModel() {

    private val _orders = MutableStateFlow<List<MerchantCustomerOrderSummaryResponse>>(emptyList())
    val orders: StateFlow<List<MerchantCustomerOrderSummaryResponse>> = _orders.asStateFlow()

    private val _selectedOrder = MutableStateFlow<MerchantCustomerOrderDetailResponse?>(null)
    val selectedOrder: StateFlow<MerchantCustomerOrderDetailResponse?> = _selectedOrder.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _updatingOrderIds = MutableStateFlow<Set<Long>>(emptySet())
    val updatingOrderIds: StateFlow<Set<Long>> = _updatingOrderIds.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadOrders(forceRefresh: Boolean = false) {
        if (_isLoading.value || _isRefreshing.value) return
        viewModelScope.launch {
            if (forceRefresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            _error.value = null
            try {
                _orders.value = storefrontOrderRepository.getOrders()
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(e, "Failed to load online orders.")
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun loadOrder(orderId: Long) {
        viewModelScope.launch {
            _error.value = null
            _updatingOrderIds.update { it + orderId }
            try {
                _selectedOrder.value = storefrontOrderRepository.getOrder(orderId)
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(e, "Failed to load online order details.")
            } finally {
                _updatingOrderIds.update { it - orderId }
            }
        }
    }

    fun clearSelectedOrder() {
        _selectedOrder.value = null
    }

    fun updateOrderStatus(orderId: Long, nextStatus: String, customerNote: String? = null) {
        viewModelScope.launch {
            _error.value = null
            _updatingOrderIds.update { it + orderId }
            try {
                val updated = storefrontOrderRepository.updateOrderStatus(orderId, nextStatus, customerNote)
                _selectedOrder.value = updated
                _orders.update { current ->
                    current.map { order ->
                        if (order.orderId == orderId) {
                            order.copy(
                                orderStatus = updated.orderStatus,
                                paymentStatus = updated.paymentStatus,
                                updatedAt = updated.updatedAt
                            )
                        } else {
                            order
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(e, "Failed to update online order.")
            } finally {
                _updatingOrderIds.update { it - orderId }
            }
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
