package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.MerchantCustomerOrderDetailResponse
import com.khanabook.lite.pos.data.remote.dto.MerchantCustomerOrderSummaryResponse
import com.khanabook.lite.pos.data.remote.dto.UpdateCustomerOrderStatusRequest
import com.khanabook.lite.pos.domain.util.BackendErrorParser
import com.khanabook.lite.pos.domain.util.BackendException
import retrofit2.HttpException

class StorefrontOrderRepository(
    private val api: KhanaBookApi
) {
    suspend fun getOrders(): List<MerchantCustomerOrderSummaryResponse> = runCatching {
        api.getStorefrontOrders()
    }.getOrElse { throw mapBackendException(it) }

    suspend fun getOrder(orderId: Long): MerchantCustomerOrderDetailResponse = runCatching {
        api.getStorefrontOrder(orderId)
    }.getOrElse { throw mapBackendException(it) }

    suspend fun updateOrderStatus(
        orderId: Long,
        status: String,
        customerNote: String? = null
    ): MerchantCustomerOrderDetailResponse = runCatching {
        api.updateStorefrontOrderStatus(
            orderId = orderId,
            request = UpdateCustomerOrderStatusRequest(
                orderStatus = status,
                customerNote = customerNote
            )
        )
    }.getOrElse { throw mapBackendException(it) }

    private fun mapBackendException(error: Throwable): Throwable {
        return when (error) {
            is BackendException -> error
            is HttpException -> BackendException(BackendErrorParser.fromHttpException(error), error)
            else -> error
        }
    }
}
