package com.khanabook.lite.pos.data.repository

import android.util.Log
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.MarketplaceOrderCounts
import com.khanabook.lite.pos.data.remote.dto.MarketplaceOrderDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.Result

/**
 * Direct REST client for marketplace orders. There is intentionally NO Room table
 * for marketplace orders — see design D13: they are server-authoritative and every
 * action (accept/reject/mark-ready/complete) requires a live connection. The bearer
 * token is attached by AuthInterceptor, so no session lookup is required here.
 */
@Singleton
class MarketplaceOrderRepository @Inject constructor(
    private val api: KhanaBookApi
) {

    suspend fun list(status: String?): Flow<List<MarketplaceOrderDto>> = flow {
        emit(fetch(status))
    }

    private suspend fun fetch(status: String?): List<MarketplaceOrderDto> = try {
        when (status) {
            "pending" -> api.listPendingMarketplaceOrders()
            else -> api.listMarketplaceOrders()
        }
    } catch (e: Exception) {
        Log.w("MpOrderRepo", "Failed to fetch marketplace orders: ${e.message}")
        emptyList()
    }

    suspend fun counts(): Result<MarketplaceOrderCounts> = try {
        val raw = api.getMarketplaceOrderCounts()
        Result.success(
            MarketplaceOrderCounts(
                pending = raw["pending"] ?: 0,
                accepted = raw["accepted"] ?: 0,
                ready = raw["ready"] ?: 0,
                completed = raw["completed"] ?: 0,
                rejected = raw["rejected"] ?: 0
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun accept(orderId: Long): Result<Unit> = runApi { api.acceptMarketplaceOrder(orderId) }

    suspend fun reject(orderId: Long, reason: String): Result<Unit> =
        runApi { api.rejectMarketplaceOrder(orderId, mapOf("reason" to reason)) }

    suspend fun markReady(orderId: Long): Result<Unit> = runApi { api.markReadyMarketplaceOrder(orderId) }

    suspend fun complete(orderId: Long): Result<Unit> = runApi { api.completeMarketplaceOrder(orderId) }

    private suspend fun <T> runApi(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
