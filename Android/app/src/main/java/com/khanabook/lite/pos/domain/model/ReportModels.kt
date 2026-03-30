package com.khanabook.lite.pos.domain.model


data class OrderLevelRow(
    val dailyId: String,
    val lifetimeId: Long,
    val billId: Long,
    val paymentMode: PaymentMode,
    val orderStatus: OrderStatus,
    val date: String
)

data class OrderDetailRow(
    val dailyNo: String,
    val lifetimeNo: Long,
    val billId: Long,
    val currentStatus: String,
    val salesAmount: String,
    val payMode: PaymentMode,
    val orderStatus: OrderStatus,
    val salesDate: Long
)

data class DailySalesReport(
    val totalSales: String,
    val totalOrders: Int,
    val cashCollected: String,
    val upiCollected: String,
    val otherCollected: String
)

data class MonthlySalesReport(
    val month: Int,
    val year: Int,
    val totalSales: String,
    val totalOrders: Int
)

data class TopSellingItem(
    val itemName: String,
    val quantitySold: Int,
    val revenue: String
)


