package com.khanabook.lite.pos.domain.model


data class OrderLevelRow(
    val dailyId: String,
    /** Canonical invoice display string: GST invoice number (e.g. "26A1-000042") for new bills,
     *  "INV{n}" for legacy bills. Never "INV0". */
    val invoiceDisplay: String,
    val billId: Long,
    val paymentMode: PaymentMode,
    val sourceChannel: String = "",
    val orderType: String = "",
    val orderStatus: OrderStatus,
    val date: String,
    val cancelReason: String = ""
)

data class OrderDetailRow(
    val dailyNo: String,
    /** Canonical invoice display string: GST invoice number (e.g. "26A1-000042") for new bills,
     *  "INV{n}" for legacy bills. Never "INV0". */
    val invoiceDisplay: String,
    val billId: Long,
    val currentStatus: String,
    val salesAmount: String,
    val orderType: String = "",
    val payMode: PaymentMode,
    val sourceChannel: String = "",
    val orderStatus: OrderStatus,
    val salesDate: Long,
    val cancelReason: String = ""
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


