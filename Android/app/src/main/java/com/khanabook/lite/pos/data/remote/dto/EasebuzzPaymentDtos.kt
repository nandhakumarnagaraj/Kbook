package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreateEasebuzzOrderRequest(
    @SerializedName("billId") val billId: Long,
    @SerializedName("restaurantId") val restaurantId: Long
)

data class CreateEasebuzzOrderResponse(
    @SerializedName("status") val status: String,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("payment_url") val paymentUrl: String? = null,
    @SerializedName("txnid") val txnId: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("code") val code: String? = null
)

data class EasebuzzPaymentStatusResponse(
    @SerializedName("billId") val billId: Long? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("paymentStatus") val paymentStatus: String? = null,
    @SerializedName("gatewayTxnId") val gatewayTxnId: String? = null,
    @SerializedName("amount") val amount: String? = null,
    @SerializedName("txnid") val txnId: String? = null,
    @SerializedName("easebuzz_id") val easebuzzId: String? = null
)

data class EasebuzzRefundRequest(
    @SerializedName("amount") val amount: String,
    @SerializedName("reason") val reason: String? = null
)

data class EasebuzzRefundResponse(
    @SerializedName("status") val status: String,
    @SerializedName("refund_id") val refundId: String? = null,
    @SerializedName("error") val error: String? = null
)
