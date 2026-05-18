package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class MarketplaceOrderDto(
    @SerializedName("id") val id: Long,
    @SerializedName("platform") val platform: String,
    @SerializedName("platformOrderId") val platformOrderId: String,
    @SerializedName("orderStatus") val orderStatus: String,
    @SerializedName("customerName") val customerName: String?,
    @SerializedName("customerPhone") val customerPhone: String?,
    @SerializedName("totalAmount") val totalAmount: BigDecimal,
    @SerializedName("subtotal") val subtotal: BigDecimal?,
    @SerializedName("taxAmount") val taxAmount: BigDecimal?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("acceptedAt") val acceptedAt: Long?,
    @SerializedName("readyAt") val readyAt: Long?,
    @SerializedName("rejectedAt") val rejectedAt: Long?
)
