package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.remote.api.*

class PaymentRepository(
    private val api: KhanaBookApi
) {
    suspend fun getEasebuzzConfig(): RestaurantPaymentConfigResponse =
        api.getEasebuzzConfig()

    suspend fun toggleEasebuzzActive(enabled: Boolean): RestaurantPaymentConfigResponse =
        api.toggleEasebuzzActive(enabled)

    suspend fun createEasebuzzOrder(request: CreateEasebuzzOrderRequest): CreateEasebuzzOrderResponse =
        api.createEasebuzzOrder(request)

    suspend fun getEasebuzzPaymentStatus(billId: Long): EasebuzzPaymentStatusResponse =
        api.getEasebuzzPaymentStatus(billId)
}
