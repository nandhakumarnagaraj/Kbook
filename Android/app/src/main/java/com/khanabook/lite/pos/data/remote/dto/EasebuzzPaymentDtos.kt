package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CreateEasebuzzOrderRequest(
    @SerializedName("billId") val billId: Long,
    @SerializedName("paymentMethod") val paymentMethod: String,
    @SerializedName("gatewayAmount") val gatewayAmount: BigDecimal? = null
)

data class CreateEasebuzzOrderResponse(
    @SerializedName("paymentId") val paymentId: Long,
    @SerializedName("billId") val billId: Long,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("currency") val currency: String,
    @SerializedName("gateway") val gateway: String,
    @SerializedName("gatewayTxnId") val gatewayTxnId: String,
    @SerializedName("checkoutUrl") val checkoutUrl: String
)

data class EasebuzzPaymentStatusResponse(
    @SerializedName("billId") val billId: Long,
    @SerializedName("paymentId") val paymentId: Long,
    @SerializedName("paymentStatus") val paymentStatus: String,
    @SerializedName("gatewayTxnId") val gatewayTxnId: String,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("message") val message: String
)
