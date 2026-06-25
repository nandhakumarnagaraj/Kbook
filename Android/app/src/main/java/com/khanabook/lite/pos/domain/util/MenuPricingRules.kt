package com.khanabook.lite.pos.domain.util

import java.math.BigDecimal
import java.math.RoundingMode

object MenuPricingRules {
    val MIN_PRICE: BigDecimal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    val MAX_PRICE: BigDecimal = BigDecimal("100000.00")
    const val ERROR_MESSAGE: String = "Price must be between Rs. 0 and Rs. 1,00,000."

    fun normalizePrice(value: String): String {
        val amount = value.ifBlank { "0" }.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Enter a valid item price")
        val normalized = amount.setScale(2, RoundingMode.HALF_UP)
        if (normalized < MIN_PRICE || normalized > MAX_PRICE) {
            throw IllegalArgumentException(ERROR_MESSAGE)
        }
        return normalized.toPlainString()
    }

    fun isValidPrice(value: Double?): Boolean {
        if (value == null) return false
        val amount = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
        return amount >= MIN_PRICE && amount <= MAX_PRICE
    }
}
