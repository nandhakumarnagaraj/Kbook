package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CreateEasebuzzOrderRequest(
    @SerializedName("billId") val billId: Long,
    @SerializedName("paymentMethod") val paymentMethod: String,
    @SerializedName("gatewayAmount") val gatewayAmount: BigDecimal? = null,
    @SerializedName("restaurantId") val restaurantId: Long
)

data class CreateEasebuzzOrderResponse(
    @SerializedName("status") val status: String,
    @SerializedName("txnid") val txnid: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("payment_url") val paymentUrl: String,
    @SerializedName("amount") val amount: BigDecimal
)

data class EasebuzzPaymentStatusResponse(
    @SerializedName("billId") val billId: Long,
    @SerializedName("paymentId") val paymentId: Long,
    @SerializedName("paymentStatus") val paymentStatus: String,
    @SerializedName("gatewayTxnId") val gatewayTxnId: String,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("message") val message: String
)

data class EasebuzzVerifyResponse(
    @SerializedName("status") val status: String,
    @SerializedName("easebuzz_id") val easebuzzId: String,
    @SerializedName("txnid") val txnid: String
)

data class EasebuzzSubMerchantStatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("kycUrl") val kycUrl: String? = null,
    @SerializedName("kycSubmissionDate") val kycSubmissionDate: String? = null,
    @SerializedName("activationDate") val activationDate: String? = null
)

data class EasebuzzRefundRequest(
    @SerializedName("amount") val amount: String,
    @SerializedName("reason") val reason: String? = null
)

data class EasebuzzRefundResponse(
    @SerializedName("status") val status: String,
    @SerializedName("easebuzz_refund_id") val easebuzzRefundId: String? = null,
    @SerializedName("error") val error: String? = null
)
