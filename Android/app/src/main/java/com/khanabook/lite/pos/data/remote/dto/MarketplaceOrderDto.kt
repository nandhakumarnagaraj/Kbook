package com.khanabook.lite.pos.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class MarketplaceOrderDto(
        @SerializedName("id") val id: Long,
        @SerializedName("restaurantId") val restaurantId: Long,
        @SerializedName("billId") val billId: Long? = null,
        @SerializedName("platform") val platform: String,
        @SerializedName("platformOrderId") val platformOrderId: String,
        @SerializedName("platformStatus") val platformStatus: String? = null,
        @SerializedName("orderStatus") val orderStatus: String,
        @SerializedName("customerName") val customerName: String? = null,
        @SerializedName("customerPhone") val customerPhone: String? = null,
        @SerializedName("customerAddress") val customerAddress: String? = null,
        @SerializedName("subtotal") val subtotal: BigDecimal? = null,
        @SerializedName("taxAmount") val taxAmount: BigDecimal? = null,
        @SerializedName("totalAmount") val totalAmount: BigDecimal? = null,
        @SerializedName("paymentMode") val paymentMode: String? = null,
        @SerializedName("acceptedAt") val acceptedAt: Long? = null,
        @SerializedName("rejectedAt") val rejectedAt: Long? = null,
        @SerializedName("rejectedReason") val rejectedReason: String? = null,
        @SerializedName("readyAt") val readyAt: Long? = null,
        @SerializedName("completedAt") val completedAt: Long? = null,
        @SerializedName("createdAt") val createdAt: Long,
        @SerializedName("updatedAt") val updatedAt: Long,
        @SerializedName("items") val items: List<MarketplaceOrderItemDto>? = null
) {
        val displayAmount: String
                get() = totalAmount?.let { "₹$it" } ?: ""
}

data class MarketplaceOrderItemDto(
        @SerializedName("id") val id: Long,
        @SerializedName("marketplaceOrderId") val marketplaceOrderId: Long,
        @SerializedName("billItemId") val billItemId: Long? = null,
        @SerializedName("platformItemId") val platformItemId: String? = null,
        @SerializedName("itemName") val itemName: String,
        @SerializedName("variantName") val variantName: String? = null,
        @SerializedName("price") val price: BigDecimal? = null,
        @SerializedName("quantity") val quantity: Int,
        @SerializedName("itemTotal") val itemTotal: BigDecimal? = null,
        @SerializedName("specialInstruction") val specialInstruction: String? = null
)

data class MarketplaceOrderCounts(
        @SerializedName("pending") val pending: Long = 0,
        @SerializedName("accepted") val accepted: Long = 0,
        @SerializedName("ready") val ready: Long = 0,
        @SerializedName("completed") val completed: Long = 0,
        @SerializedName("rejected") val rejected: Long = 0
)
