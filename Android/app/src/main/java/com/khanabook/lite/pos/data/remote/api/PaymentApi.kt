package com.khanabook.lite.pos.data.remote.api

data class SaveRestaurantPaymentConfigRequest(
    val merchantKey: String,
    val salt: String,
    val environment: String
)

data class RestaurantPaymentConfigResponse(
    val restaurantId: Long,
    val gateway: String,
    val merchantKeyMasked: String,
    val environment: String,
    val active: Boolean
)

data class CreateEasebuzzOrderRequest(
    val billId: Long,
    val paymentMethod: String,
    val gatewayAmount: String? = null
)

data class CreateEasebuzzOrderResponse(
    val paymentId: Long,
    val billId: Long,
    val amount: String,
    val currency: String,
    val gateway: String,
    val gatewayTxnId: String,
    val checkoutUrl: String
)

data class EasebuzzPaymentStatusResponse(
    val billId: Long,
    val paymentId: Long,
    val paymentStatus: String,
    val gatewayTxnId: String,
    val amount: String,
    val message: String
)
