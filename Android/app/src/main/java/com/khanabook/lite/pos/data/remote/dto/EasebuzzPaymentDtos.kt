package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName

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

data class CreateEasebuzzPaymentLinkRequest(
    @SerializedName("restaurantId") val restaurantId: Long,
    @SerializedName("amount") val amount: String,
    @SerializedName("customerName") val customerName: String,
    @SerializedName("customerEmail") val customerEmail: String,
    @SerializedName("customerPhone") val customerPhone: String,
    @SerializedName("message") val message: String,
    @SerializedName("merchantTxn") val merchantTxn: String? = null,
    @SerializedName("show_payment_mode") val showPaymentMode: String? = null
)

data class CreatePaymentLinkForBillRequest(
    @SerializedName("billId") val billId: Long,
    @SerializedName("restaurantId") val restaurantId: Long,
    @SerializedName("customerPhone") val customerPhone: String? = null,
    @SerializedName("customerEmail") val customerEmail: String? = null
)
