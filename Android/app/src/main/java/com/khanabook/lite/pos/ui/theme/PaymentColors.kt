package com.khanabook.lite.pos.ui.theme

import androidx.compose.ui.graphics.Color
import com.khanabook.lite.pos.domain.model.PaymentMode

/**
 * Single source of truth for payment mode → color mapping.
 * Used by ReportsScreen, OrdersScreen, NewBillScreen, OrderConfirmationSection.
 */
fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> SuccessGreen
        PaymentMode.UPI -> Brown500
        PaymentMode.POS -> PrimaryGold
        PaymentMode.EASEBUZZ -> Color(0xFF1976D2) // Blue for online payments
        PaymentMode.PART_CASH_UPI -> PrimaryGold
        PaymentMode.PART_CASH_POS -> PrimaryGold
        PaymentMode.PART_UPI_POS -> PrimaryGold
    }
}

/**
 * Overload accepting raw String payment mode value for screens that
 * work with database string values directly.
 */
fun payModeColor(mode: String?): Color {
    if (mode == null) return Brown500
    return when (mode.lowercase()) {
        "cash" -> SuccessGreen
        "upi" -> Brown500
        "pos", "card" -> PrimaryGold
        "part_cash_upi", "part_payment_upi_cash", "part_cash_pos", "part_payment_cash_pos", "part_upi_pos", "part_payment_upi_pos" -> PrimaryGold
        else -> Brown500
    }
}
