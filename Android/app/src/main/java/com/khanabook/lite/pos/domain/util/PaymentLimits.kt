package com.khanabook.lite.pos.domain.util

import java.math.BigDecimal

object PaymentLimits {
    val UPI_SINGLE_TRANSACTION_MAX: BigDecimal = BigDecimal("100000.00")
    const val UPI_LIMIT_MESSAGE: String =
        "UPI amount cannot be greater than Rs. 1,00,000. Use split payment for the remaining amount."
}
