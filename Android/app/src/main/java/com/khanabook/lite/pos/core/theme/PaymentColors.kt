package com.khanabook.lite.pos.core.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.khanabook.lite.pos.domain.model.PaymentMode

/**
 * Single source of truth for payment mode → color mapping.
 * Used by ReportsScreen, OrdersScreen, NewBillScreen, OrderConfirmationSection.
 *
 * Visual language: Cash=green, UPI=brown, POS=gold, Online=blue. Each part-payment
 * combination gets its own hue (Cash+UPI=teal, Cash+POS=orange, UPI+POS=sky) so the
 * three split rows never read as one undifferentiated block.
 */
fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> SuccessGreen
        PaymentMode.UPI -> Brown500
        PaymentMode.POS -> PrimaryGold
        PaymentMode.EASEBUZZ, PaymentMode.PAYMENT_LINK -> SmsBlue
        PaymentMode.PART_CASH_UPI -> PartCashUpiTeal
        PaymentMode.PART_CASH_POS -> PartCashPosOrange
        PaymentMode.PART_UPI_POS -> PartUpiPosSky
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
        // PartCashPosOrange is a warm hue on a warm surface — same lift, keeps
        // Cash+POS readable while staying clearly orange next to POS's gold.
        PaymentMode.PART_CASH_POS -> Color(0xFFFF9433)
        else -> getPayModeColor(mode)
    }
}

/**
 * Single source of truth for payment mode → icon mapping.
 * Used by the Payments Report cards, order mode badges and the payment step.
 *
 * Single-tender modes get the glyph of the tender itself. The three part-payment
 * combos each get their OWN glyph so a report row is identifiable by icon alone,
 * without reading the label: all three are the same concept (settled across two
 * tenders) and are drawn from one family of route/compare/exchange marks.
 */
fun getPayModeIcon(mode: PaymentMode): ImageVector {
    return when (mode) {
        PaymentMode.CASH -> Icons.Default.Payments
        PaymentMode.UPI -> Icons.Default.QrCode2
        PaymentMode.POS -> Icons.Default.PointOfSale
        PaymentMode.EASEBUZZ, PaymentMode.PAYMENT_LINK -> Icons.Default.CreditCard
        // Cash + UPI — rupee arrows, cash settling into a digital handle
        PaymentMode.PART_CASH_UPI -> Icons.Default.CurrencyExchange
        // Cash + POS — cash leg topping up a card machine
        PaymentMode.PART_CASH_POS -> Icons.AutoMirrored.Filled.AltRoute
        // UPI + POS — QR leg settling through a card machine
        PaymentMode.PART_UPI_POS -> Icons.AutoMirrored.Filled.CompareArrows
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
        "part_cash_upi", "part_payment_upi_cash" -> PartCashUpiTeal
        "part_cash_pos", "part_payment_cash_pos" -> PartCashPosOrange
        "part_upi_pos", "part_payment_upi_pos" -> PartUpiPosSky
        else -> Brown500
    }
}
