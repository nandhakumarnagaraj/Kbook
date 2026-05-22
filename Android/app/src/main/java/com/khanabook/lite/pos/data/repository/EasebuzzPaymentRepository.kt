package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderRequest
import com.khanabook.lite.pos.data.remote.dto.CreateEasebuzzOrderResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzPaymentStatusResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzSubMerchantStatusResponse
import com.khanabook.lite.pos.data.remote.dto.EasebuzzVerifyResponse
import com.khanabook.lite.pos.domain.manager.SessionManager
import java.math.BigDecimal

class EasebuzzPaymentRepository(
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager
) {
    suspend fun createOrder(
        restaurantId: Long,
        localBillId: Long,
        paymentMethod: String = "ONLINE",
        gatewayAmount: BigDecimal? = null
    ): CreateEasebuzzOrderResponse =
        api.createEasebuzzOrder(
            deviceId = sessionManager.getDeviceId(),
            request = CreateEasebuzzOrderRequest(
                billId = localBillId,
                paymentMethod = paymentMethod,
                gatewayAmount = gatewayAmount,
                restaurantId = restaurantId
            )
        )

    suspend fun getStatus(
        localBillId: Long,
        refresh: Boolean = false
    ): EasebuzzPaymentStatusResponse =
        api.getEasebuzzPaymentStatus(
            deviceId = sessionManager.getDeviceId(),
            billId = localBillId,
            refresh = refresh
        )

    suspend fun verify(localBillId: Long): EasebuzzVerifyResponse =
        api.verifyEasebuzzPayment(
            billId = localBillId,
            deviceId = sessionManager.getDeviceId()
        )

    suspend fun getSubMerchantStatus(): EasebuzzSubMerchantStatusResponse =
        api.getEasebuzzSubMerchantStatus(
            deviceId = sessionManager.getDeviceId()
        )

    suspend fun refund(
        localBillId: Long,
        amount: java.math.BigDecimal,
        reason: String? = null
    ): com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundResponse =
        api.refundEasebuzzPayment(
            billId = localBillId,
            deviceId = sessionManager.getDeviceId(),
            request = com.khanabook.lite.pos.data.remote.dto.EasebuzzRefundRequest(
                amount = amount.toString(),
                reason = reason
            )
        )
}
