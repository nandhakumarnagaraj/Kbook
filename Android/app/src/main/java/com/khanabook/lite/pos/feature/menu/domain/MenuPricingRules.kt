package com.khanabook.lite.pos.feature.menu.domain

import java.math.BigDecimal
import java.math.RoundingMode

object MenuPricingRules {
    val MIN_PRICE: BigDecimal = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP)
    val MAX_PRICE: BigDecimal = BigDecimal("100000.00")
    const val ERROR_MESSAGE: String = "Price must be between Rs. 1 and Rs. 1,00,000."

    // Bounds the server actually enforces (NUMERIC(12,2) CHECK (price >= 0)), not the band
    // this app happens to use when authoring. See isSyncedPriceText.
    private val SYNCED_MIN_PRICE: BigDecimal = BigDecimal.ZERO
    private val SYNCED_MAX_PRICE: BigDecimal = BigDecimal("9999999999.99")

    fun normalizePrice(value: String): String {
        val amount = value.ifBlank { "0" }.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Enter a valid item price")
        val normalized = amount.setScale(2, RoundingMode.HALF_UP)
        if (normalized < MIN_PRICE || normalized > MAX_PRICE) {
            throw IllegalArgumentException(ERROR_MESSAGE)
        }
        return normalized.toPlainString()
    }

    /**
     * A variant item can be saved with no base price typed because the variants carry the
     * real prices, yet the parent row still needs a valid base price (the menu grid shows it,
     * billing falls back to it). Falls back to the cheapest variant without widening the band.
     */
    fun resolveBasePrice(basePrice: String?, variantPrices: List<Double>): String {
        normalizeIfValid(basePrice)?.let { return it }
        val cheapestVariant = variantPrices.filter { isValidPrice(it) }.minOrNull()
            ?: return basePrice.orEmpty()
        return normalizePrice(cheapestVariant.toString())
    }

    private fun normalizeIfValid(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return try {
            normalizePrice(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun isValidPrice(value: Double?): Boolean {
        if (value == null) return false
        val amount = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
        return amount >= MIN_PRICE && amount <= MAX_PRICE
    }

    /**
     * Range check for a price arriving from the server during sync. This band is deliberately
     * WIDER than the authoring band above and must not be tightened to match it.
     *
     * The server column is NUMERIC(12,2) with CHECK (price >= 0) and no ceiling, so a banquet
     * package well above MAX_PRICE, and a zero-priced promo item, are both legal there. Reusing
     * the authoring band here would silently and permanently drop valid items from the menu.
     * A blank is rejected because the column is NOT NULL.
     */
    fun isSyncedPriceText(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val amount = value.toBigDecimalOrNull() ?: return false
        return amount >= SYNCED_MIN_PRICE && amount <= SYNCED_MAX_PRICE
    }
}
