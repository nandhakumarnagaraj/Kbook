package com.khanabook.lite.pos.feature.payments.data

import com.google.gson.annotations.SerializedName

data class EasebuzzRefundRequest(
    @SerializedName("amount") val amount: String,
    @SerializedName("reason") val reason: String? = null
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
