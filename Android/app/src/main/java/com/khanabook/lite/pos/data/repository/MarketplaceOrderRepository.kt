package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.MarketplaceOrderDto
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.util.BackendErrorParser
import com.khanabook.lite.pos.domain.util.BackendException
import retrofit2.HttpException

class MarketplaceOrderRepository(
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager
) {
    private val deviceId: String get() = sessionManager.getDeviceId()

    suspend fun getOrders(): List<MarketplaceOrderDto> = runCatching {
        api.getMarketplaceOrders(deviceId)
    }.getOrElse { throw mapBackendException(it) }

    suspend fun getPendingOrders(): List<MarketplaceOrderDto> = runCatching {
        api.getPendingMarketplaceOrders(deviceId)
    }.getOrElse { throw mapBackendException(it) }

    suspend fun getOrderCounts(): Map<String, Long> = runCatching {
        api.getMarketplaceOrderCounts(deviceId)
    }.getOrElse { throw mapBackendException(it) }

    suspend fun acceptOrder(orderId: Long): MarketplaceOrderDto = runCatching {
        api.acceptMarketplaceOrder(orderId, deviceId)
    }.getOrElse { throw mapBackendException(it) }

    suspend fun rejectOrder(orderId: Long, reason: String): MarketplaceOrderDto = runCatching {
        api.rejectMarketplaceOrder(orderId, deviceId, mapOf("reason" to reason))
    }.getOrElse { throw mapBackendException(it) }

    suspend fun markReady(orderId: Long): MarketplaceOrderDto = runCatching {
        api.markMarketplaceOrderReady(orderId, deviceId)
    }.getOrElse { throw mapBackendException(it) }

    private fun mapBackendException(error: Throwable): Throwable {
        return when (error) {
            is BackendException -> error
            is HttpException -> BackendException(BackendErrorParser.fromHttpException(error), error)
            else -> error
        }
    }
}
