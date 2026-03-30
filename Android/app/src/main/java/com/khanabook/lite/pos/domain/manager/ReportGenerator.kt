package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.model.*
import com.khanabook.lite.pos.domain.util.DateUtils
import kotlinx.coroutines.flow.firstOrNull
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Handles the generation of various reports and data mappings for display.
 */
class ReportGenerator(private val billRepository: BillRepository) {

    suspend fun getPaymentBreakdown(from: Long, to: Long): Map<String, String> {
        val bills = billRepository.getBillsByDateRange(from, to).firstOrNull() ?: emptyList()
        val breakdown = mutableMapOf<String, BigDecimal>()
        
        for (bill in bills) {
            if (OrderStatus.fromDbValue(bill.orderStatus) != OrderStatus.COMPLETED) continue
            
            val mode = PaymentMode.fromDbValue(bill.paymentMode)
            val amount = BigDecimal(bill.totalAmount)
            val label = mode.displayLabel
            
            if (PaymentModeManager.isPartPayment(mode)) {
                val labels = PaymentModeManager.getPartLabels(mode)
                val p1 = BigDecimal(bill.partAmount1)
                val p2 = BigDecimal(bill.partAmount2)

                breakdown[labels.first] = (breakdown[labels.first] ?: BigDecimal.ZERO).add(p1)
                breakdown[labels.second] = (breakdown[labels.second] ?: BigDecimal.ZERO).add(p2)
                
                breakdown[label] = (breakdown[label] ?: BigDecimal.ZERO).add(amount)
                
                val part1Key = "${label}_part1"
                val part2Key = "${label}_part2"
                breakdown[part1Key] = (breakdown[part1Key] ?: BigDecimal.ZERO).add(p1)
                breakdown[part2Key] = (breakdown[part2Key] ?: BigDecimal.ZERO).add(p2)
            } else {
                breakdown[label] = (breakdown[label] ?: BigDecimal.ZERO).add(amount)
            }
        }
        return breakdown.mapValues { it.value.setScale(2, RoundingMode.HALF_UP).toString() }
    }

    suspend fun getOrderLevelRows(from: Long, to: Long): List<OrderLevelRow> {
        val bills = billRepository.getBillsByDateRange(from, to).firstOrNull() ?: emptyList()
        return bills.filter { 
            val status = OrderStatus.fromDbValue(it.orderStatus)
            status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED 
        }
            .map { bill ->
                OrderLevelRow(
                    dailyId = bill.dailyOrderDisplay.split("-").last(),
                    lifetimeId = bill.lifetimeOrderId,
                    billId = bill.id,
                    paymentMode = PaymentMode.fromDbValue(bill.paymentMode),
                    orderStatus = OrderStatus.fromDbValue(bill.orderStatus),
                    date = DateUtils.formatDisplay(bill.createdAt)
                )
            }
    }

    suspend fun getOrderDetail(billId: Long): BillWithItems? {
        return billRepository.getBillWithItemsById(billId)
    }

    suspend fun getOrderDetailsTable(from: Long, to: Long): List<OrderDetailRow> {
        val bills = billRepository.getBillsByDateRange(from, to).firstOrNull() ?: emptyList()
        return bills.map { bill ->
            OrderDetailRow(
                dailyNo = bill.dailyOrderDisplay.split("-").last(),
                lifetimeNo = bill.lifetimeOrderId,
                billId = bill.id,
                currentStatus = formatCurrentStatus(bill),
                salesAmount = bill.totalAmount,
                payMode = PaymentMode.fromDbValue(bill.paymentMode),
                orderStatus = OrderStatus.fromDbValue(bill.orderStatus),
                salesDate = bill.createdAt
            )
        }
    }

    fun formatCurrentStatus(bill: BillEntity): String {
        val status = OrderStatus.fromDbValue(bill.orderStatus).name.lowercase().replaceFirstChar { it.uppercase() }
        val payMode = PaymentMode.fromDbValue(bill.paymentMode).displayLabel
        return "Order $status [$payMode]"
    }

    suspend fun getDailyReport(date: String): DailySalesReport {
        val startOfDay = "$date 00:00:00"
        val endOfDay = "$date 23:59:59"
        val bills = billRepository.getBillsByDateRange(startOfDay, endOfDay).firstOrNull() ?: emptyList()
        
        var totalSales = BigDecimal.ZERO
        var cash = BigDecimal.ZERO
        var upi = BigDecimal.ZERO
        var other = BigDecimal.ZERO
        
        for (bill in bills) {
            if (OrderStatus.fromDbValue(bill.orderStatus) == OrderStatus.COMPLETED) {
                val amount = BigDecimal(bill.totalAmount)
                totalSales = totalSales.add(amount)
                
                val mode = PaymentMode.fromDbValue(bill.paymentMode)
                when (mode) {
                    PaymentMode.CASH -> cash = cash.add(amount)
                    PaymentMode.UPI -> upi = upi.add(amount)
                    PaymentMode.PART_CASH_UPI -> {
                        cash = cash.add(BigDecimal(bill.partAmount1))
                        upi = upi.add(BigDecimal(bill.partAmount2))
                    }
                    else -> other = other.add(amount)
                }
            }
        }
        
        return DailySalesReport(
            totalSales = totalSales.setScale(2, RoundingMode.HALF_UP).toString(),
            totalOrders = bills.size,
            cashCollected = cash.setScale(2, RoundingMode.HALF_UP).toString(),
            upiCollected = upi.setScale(2, RoundingMode.HALF_UP).toString(),
            otherCollected = other.setScale(2, RoundingMode.HALF_UP).toString()
        )
    }

    suspend fun getMonthlyReport(month: Int, year: Int): MonthlySalesReport {
        val monthStr = month.toString().padStart(2, '0')
        val startOfMonth = "$year-$monthStr-01 00:00:00"
        val endOfMonth = "$year-$monthStr-31 23:59:59"
        
        val bills = billRepository.getBillsByDateRange(startOfMonth, endOfMonth).firstOrNull() ?: emptyList()
        
        var totalSales = BigDecimal.ZERO
        var completedOrders = 0
        
        for (bill in bills) {
            if (OrderStatus.fromDbValue(bill.orderStatus) == OrderStatus.COMPLETED) {
                totalSales = totalSales.add(BigDecimal(bill.totalAmount))
                completedOrders++
            }
        }
        
        return MonthlySalesReport(
            month = month,
            year = year,
            totalSales = totalSales.setScale(2, RoundingMode.HALF_UP).toString(),
            totalOrders = completedOrders
        )
    }

    suspend fun getTopSellingItems(from: Long, to: Long, limit: Int): List<TopSellingItem> {
        return billRepository.getTopSellingItemsInRange(from, to, limit)
    }
}
