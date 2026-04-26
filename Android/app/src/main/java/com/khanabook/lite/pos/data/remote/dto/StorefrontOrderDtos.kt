package com.khanabook.lite.pos.data.remote.dto

data class MerchantCustomerOrderSummaryResponse(
    val orderId: Long,
    val publicOrderCode: String,
    val customerName: String,
    val customerPhone: String? = null,
    val fulfillmentType: String,
    val orderStatus: String,
    val paymentStatus: String,
    val paymentMethod: String,
    val sourceChannel: String,
    val currency: String,
    val totalAmount: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class MerchantCustomerOrderDetailResponse(
    val orderId: Long,
    val restaurantId: Long,
    val publicOrderCode: String,
    val trackingToken: String,
    val customerName: String,
    val customerPhone: String? = null,
    val customerNote: String? = null,
    val fulfillmentType: String,
    val orderStatus: String,
    val paymentStatus: String,
    val paymentMethod: String,
    val sourceChannel: String,
    val currency: String,
    val subtotal: String,
    val totalAmount: String,
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<MerchantCustomerOrderItem>
)

data class MerchantCustomerOrderItem(
    val menuItemId: Long? = null,
    val itemVariantId: Long? = null,
    val itemName: String,
    val variantName: String? = null,
    val quantity: Int,
    val unitPrice: String,
    val lineTotal: String,
    val specialInstruction: String? = null
)

data class UpdateCustomerOrderStatusRequest(
    val orderStatus: String,
    val customerNote: String? = null
)
