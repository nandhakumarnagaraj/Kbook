package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.PaymentMode

/**
 * Decides whether a UPI payment should go through the live Easebuzz gateway
 * or fall back to the manual QR + counter-confirmation flow.
 *
 * Manual flow stays available unconditionally as the offline-safe fallback.
 */
object PaymentGatewayHelper {

    fun isUpiSelection(mode: PaymentMode): Boolean =
        mode == PaymentMode.UPI ||
            mode == PaymentMode.PART_CASH_UPI ||
            mode == PaymentMode.PART_UPI_POS

    fun shouldUseGateway(
        profile: RestaurantProfileEntity?,
        mode: PaymentMode,
        isOnline: Boolean
    ): Boolean {
        if (profile == null) return false
        if (!profile.easebuzzEnabled) return false
        if (!isOnline) return false
        return isUpiSelection(mode)
    }

    fun gatewayPaymentMethod(mode: PaymentMode): String = when (mode) {
        PaymentMode.PART_CASH_UPI -> "PART_CASH_UPI"
        PaymentMode.PART_UPI_POS -> "PART_UPI_POS"
        else -> "UPI"
    }
}
