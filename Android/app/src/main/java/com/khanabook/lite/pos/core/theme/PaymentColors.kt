package com.khanabook.lite.pos.core.theme

import androidx.compose.ui.graphics.Color
import com.khanabook.lite.pos.domain.model.PaymentMode

/**
 * Single source of truth for payment mode → color mapping.
 * Used by ReportsScreen, OrdersScreen, NewBillScreen, OrderConfirmationSection.
 *
 * Visual language: Cash=green, UPI=brown, POS=gold, Online=blue, Part-payment=purple.
 */
fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> SuccessGreen
        PaymentMode.UPI -> Brown500
        PaymentMode.POS -> PrimaryGold
        PaymentMode.EASEBUZZ, PaymentMode.PAYMENT_LINK -> SmsBlue
        PaymentMode.PART_CASH_UPI, PaymentMode.PART_CASH_POS, PaymentMode.PART_UPI_POS -> BrandPurple
    }
}

/**
 * Icon-tint variant of the payment mode color, for small mode icons drawn on
 * warm card backgrounds (CardBG). Badge colors like UPI's Brown500 vanish on
 * brown surfaces — this lifts dark hues just enough to stay readable while
 * keeping the same hue identity (badge fills elsewhere are unaffected).
 */
fun getPayModeIconTint(mode: PaymentMode): Color {
    return when (mode) {
        // Brown500 on CardBG is brown-on-brown; light gold-brown keeps UPI's QR visible
        PaymentMode.UPI -> Color(0xFFD7A86E)
        else -> getPayModeColor(mode)
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
        "easebuzz", "payment_link", "online" -> SmsBlue
        "part_cash_upi", "part_payment_upi_cash", "part_cash_pos", "part_payment_cash_pos", "part_upi_pos", "part_payment_upi_pos" -> BrandPurple
        else -> Brown500
    }
}
