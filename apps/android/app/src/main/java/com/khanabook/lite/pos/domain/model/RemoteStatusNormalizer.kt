package com.khanabook.lite.pos.domain.model

/**
 * Normalises status, payment and mode strings that arrive from the server.
 *
 * Two callers, one alias table:
 *  - the pull path (`MasterSyncProcessor`) uses the `*OrDefault` functions before writing a bill,
 *    so the raw spelling never reaches the database;
 *  - the enums' `fromDbValue` uses the `orNull` functions, so rows written before this existed
 *    ("PAID", "Completed", "Success") read correctly instead of falling back to draft/failed.
 *
 * Why the aliases are explicit: routing `paid` through a plain enum lookup mapped it to DRAFT,
 * because the enum only knows `draft/completed/cancelled`. On a second terminal that turned pulled
 * bills into "Preparing" and dropped their revenue (BUG-042).
 */
object RemoteStatusNormalizer {

    fun orderStatusOrNull(raw: String?): OrderStatus? = when (raw?.trim()?.lowercase()) {
        // A refunded bill is still a completed sale: the money came in, and the refund itself is
        // tracked separately in `refund_amount`. It must not be reclassified as cancelled, which
        // would drop it out of revenue.
        "completed", "complete", "paid", "settled", "closed", "refunded" -> OrderStatus.COMPLETED
        "cancelled", "canceled", "void", "voided" -> OrderStatus.CANCELLED
        "draft", "open", "preparing", "pending", "in_progress" -> OrderStatus.DRAFT
        else -> null
    }

    /**
     * Unknown or blank input keeps the historical pull default (`completed`), so normalising
     * spelling does not move any existing bill into or out of revenue.
     */
    fun orderStatus(raw: String?, unknownDefault: OrderStatus = OrderStatus.COMPLETED): String =
        (orderStatusOrNull(raw) ?: unknownDefault).dbValue

    fun paymentStatusOrNull(raw: String?): PaymentStatus? = when (raw?.trim()?.lowercase()) {
        "success", "succeeded", "successful", "paid", "complete", "completed", "captured" ->
            PaymentStatus.SUCCESS
        "pending", "initiated", "processing", "awaiting", "in_progress" -> PaymentStatus.PENDING
        "failed", "failure", "cancelled", "canceled", "declined", "refunded" -> PaymentStatus.FAILED
        else -> null
    }

    fun paymentStatus(raw: String?, unknownDefault: PaymentStatus = PaymentStatus.SUCCESS): String =
        (paymentStatusOrNull(raw) ?: unknownDefault).dbValue

    fun paymentMode(raw: String?): String {
        val text = raw?.trim()?.lowercase().orEmpty()
        if (text.isEmpty()) return PaymentMode.CASH.dbValue

        PaymentMode.values().firstOrNull {
            it.dbValue.equals(text, ignoreCase = true) || it.name.equals(text, ignoreCase = true)
        }?.let { return it.dbValue }

        // The server may send a label ("POS Machine", "Cash + UPI") instead of the db value.
        return when (text) {
            "pos machine" -> PaymentMode.POS.dbValue
            "pay online" -> PaymentMode.EASEBUZZ.dbValue
            "send payment link", "payment link", "link" -> PaymentMode.PAYMENT_LINK.dbValue
            "cash + upi", "cash+upi", "part cash upi" -> PaymentMode.PART_CASH_UPI.dbValue
            "cash + pos", "cash+pos", "part cash pos" -> PaymentMode.PART_CASH_POS.dbValue
            "upi + pos", "upi+pos", "part upi pos" -> PaymentMode.PART_UPI_POS.dbValue
            else -> PaymentMode.CASH.dbValue
        }
    }
}
