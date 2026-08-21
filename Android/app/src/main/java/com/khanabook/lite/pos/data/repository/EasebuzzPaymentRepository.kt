package com.khanabook.lite.pos.data.repository

import android.util.Log
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderRequest
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderResponse
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzPaymentLinkRequest
import com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EasebuzzPaymentRepository @Inject constructor(
    private val api: KhanaBookApi
) {

    suspend fun createOrder(billId: Long, restaurantId: Long): Result<CreateEasebuzzOrderResponse> =
        runApi {
            api.createEasebuzzOrder(
                CreateEasebuzzOrderRequest(billId = billId, restaurantId = restaurantId)
            )
        }

    suspend fun getPaymentStatus(billId: Long, refresh: Boolean = false): Result<Map<String, Any?>> =
        runApi { api.getEasebuzzPaymentStatus(billId, refresh) }

    suspend fun verifyPayment(billId: Long): Result<Map<String, Any?>> =
        runApi { api.verifyEasebuzzPayment(billId) }

    suspend fun refund(billId: Long, amount: String, reason: String? = null): Result<Map<String, Any?>> =
        runApi { api.refundEasebuzzPayment(billId, EasebuzzRefundRequest(amount, reason)) }

    suspend fun getRefundStatus(billId: Long): Result<Map<String, Any?>> =
        runApi { api.getEasebuzzRefundStatus(billId) }

    suspend fun cancelPayment(billId: Long): Result<Map<String, Any?>> =
        runApi { api.cancelEasebuzzPayment(billId) }

    suspend fun createPaymentLink(
        restaurantId: Long,
        amount: String,
        customerName: String,
        customerEmail: String,
        customerPhone: String,
        message: String,
        merchantTxn: String
    ): Result<Map<String, Any?>> =
        runApi {
            val request = CreateEasebuzzPaymentLinkRequest(
                restaurantId = restaurantId,
                amount = amount,
                customerName = customerName,
                customerEmail = customerEmail,
                customerPhone = customerPhone,
                message = message,
                merchantTxn = merchantTxn
            )
            api.createEasebuzzPaymentLink(request)
        }

    suspend fun createPaymentLinkForBill(
        billId: Long,
        restaurantId: Long
    ): Result<Map<String, Any?>> =
        runApi {
            api.createPaymentLinkForBill(
                com.khanabook.lite.pos.data.remote.dto.CreatePaymentLinkForBillRequest(
                    billId = billId,
                    restaurantId = restaurantId
                )
            )
        }

    private suspend fun <T> runApi(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Exception) {
        Log.w("EasebuzzPaymentRepo", "API call failed: ${e.message}")
        Result.failure(e)
    }
}
