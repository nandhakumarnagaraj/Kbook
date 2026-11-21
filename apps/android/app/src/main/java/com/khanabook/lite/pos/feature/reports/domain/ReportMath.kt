package com.khanabook.lite.pos.feature.reports.domain

import com.khanabook.lite.pos.feature.billing.data.BillEntity
import java.math.BigDecimal

/**
 * Net revenue for a bill = gross total minus any admin-recorded refund, clamped at zero so a
 * refund larger than the bill cannot turn the day's takings negative.
 *
 * `refundAmount` is server-owned (nullable string) and arrives through the pull path.
 *
 * Both the Home summary tiles and [ReportGenerator] use this one function. They previously each
 * had their own definition — Home summed the raw `total_amount` as a Double and ignored refunds —
 * so "today's sales" could be two different numbers (BUG-043).
 */
fun BillEntity.netAmount(): BigDecimal {
    val gross = totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val refund = refundAmount
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { BigDecimal(it) }.getOrNull() }
        ?: BigDecimal.ZERO
    return gross.subtract(refund).max(BigDecimal.ZERO)
}
