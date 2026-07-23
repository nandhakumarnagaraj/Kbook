package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface PaymentRecoveryAssessment {
    data object Empty : PaymentRecoveryAssessment
    data class Complete(val paidAmount: String) : PaymentRecoveryAssessment
    data class Partial(
        val paidAmount: String,
        val remainingAmount: String,
        val usedModes: Set<String>
    ) : PaymentRecoveryAssessment
    data class Conflicting(val reason: String) : PaymentRecoveryAssessment
}

object PaymentSetValidator {
    private val moneyScale = 2
    private val supportedModes = setOf("cash", "upi", "pos")

    fun validate(payments: List<BillPaymentEntity>, payableTotal: String): Result<Unit> = runCatching {
        require(payments.isNotEmpty()) { "At least one payment is required." }
        val expected = parseMoney(payableTotal, "Bill total")
        require(expected > BigDecimal.ZERO) { "Bill total must be greater than zero." }

        val modes = mutableSetOf<String>()
        val operationIds = mutableSetOf<String>()
        val actual = payments.filterNot { it.isDeleted }.fold(BigDecimal.ZERO) { total, payment ->
            require(payment.paymentMode.isNotBlank()) { "Payment mode is required." }
            require(payment.paymentMode in supportedModes) { "Unsupported payment mode: ${payment.paymentMode}." }
            require(!payment.operationId.isNullOrBlank()) { "Payment identity is required." }
            require(operationIds.add(payment.operationId!!)) {
                "Duplicate payment identity: ${payment.operationId}."
            }
            require(modes.add(payment.paymentMode)) { "Duplicate payment component: ${payment.paymentMode}." }
            val amount = parseMoney(payment.amount, payment.paymentMode)
            require(amount > BigDecimal.ZERO) { "${payment.paymentMode} amount must be greater than zero." }
            total.add(amount)
        }.setScale(moneyScale, RoundingMode.UNNECESSARY)

        require(actual.compareTo(expected) == 0) {
            "Payment total ${actual.toPlainString()} does not match bill total ${expected.toPlainString()}."
        }
    }

    fun equivalent(
        existing: List<BillPaymentEntity>,
        requested: List<BillPaymentEntity>,
        payableTotal: String
    ): Boolean {
        val activeExisting = existing.filterNot { it.isDeleted }
        val activeRequested = requested.filterNot { it.isDeleted }
        if (validate(activeExisting, payableTotal).isFailure) return false
        if (validate(activeRequested, payableTotal).isFailure) return false

        // Compare mode-to-amount signature (ignores row order).
        fun amountSignature(rows: List<BillPaymentEntity>) =
            rows.associate { it.paymentMode to parseMoney(it.amount, it.paymentMode).toPlainString() }
        if (amountSignature(activeExisting) != amountSignature(activeRequested)) return false

        // Every requested row must have a semantically matching existing row.
        return activeRequested.all { requestedPayment ->
            activeExisting.any { existingRow ->
                // Core identity: operationId and paymentMode must match.
                existingRow.operationId == requestedPayment.operationId &&
                    existingRow.paymentMode == requestedPayment.paymentMode &&
                    // Normalized amount must match (already validated as an exact set above
                    // via amountSignature, but also check per-row for individual consistency).
                    parseMoney(existingRow.amount, existingRow.paymentMode)
                        .compareTo(parseMoney(requestedPayment.amount, requestedPayment.paymentMode)) == 0 &&
                    // Ownership must match where stored.
                    (existingRow.billPublicToken == null || requestedPayment.billPublicToken == null ||
                        existingRow.billPublicToken == requestedPayment.billPublicToken) &&
                    (existingRow.restaurantId == 0L || requestedPayment.restaurantId == 0L ||
                        existingRow.restaurantId == requestedPayment.restaurantId) &&
                    (existingRow.terminalId == null || requestedPayment.terminalId == null ||
                        existingRow.terminalId == requestedPayment.terminalId) &&
                    // Verification source must match when both are non-null.
                    (existingRow.verifiedBy == requestedPayment.verifiedBy) &&
                    // Gateway identity must match when both are non-null.
                    (existingRow.gatewayTxnId == null || requestedPayment.gatewayTxnId == null ||
                        existingRow.gatewayTxnId == requestedPayment.gatewayTxnId) &&
                    (existingRow.gatewayStatus == null || requestedPayment.gatewayStatus == null ||
                        existingRow.gatewayStatus == requestedPayment.gatewayStatus)
            }
        }
    }

    fun assessForRecovery(
        payments: List<BillPaymentEntity>,
        payableTotal: String
    ): PaymentRecoveryAssessment {
        val activePayments = payments.filterNot { it.isDeleted }
        if (activePayments.isEmpty()) return PaymentRecoveryAssessment.Empty

        return try {
            val expected = parseMoney(payableTotal, "Bill total")
            require(expected > BigDecimal.ZERO) { "Bill total must be greater than zero." }

            val modes = mutableSetOf<String>()
            val operationIds = mutableSetOf<String>()
            val paid = activePayments.fold(BigDecimal.ZERO) { total, payment ->
                require(payment.paymentMode in supportedModes) {
                    "Unsupported payment mode: ${payment.paymentMode}."
                }
                require(!payment.operationId.isNullOrBlank()) { "Payment identity is required." }
                require(operationIds.add(payment.operationId!!)) {
                    "Duplicate payment identity: ${payment.operationId}."
                }
                require(modes.add(payment.paymentMode)) {
                    "Duplicate payment component: ${payment.paymentMode}."
                }
                val amount = parseMoney(payment.amount, payment.paymentMode)
                require(amount > BigDecimal.ZERO) {
                    "${payment.paymentMode} amount must be greater than zero."
                }
                total.add(amount)
            }.setScale(moneyScale, RoundingMode.UNNECESSARY)

            when {
                paid.compareTo(expected) == 0 ->
                    PaymentRecoveryAssessment.Complete(paid.toPlainString())
                paid < expected ->
                    PaymentRecoveryAssessment.Partial(
                        paidAmount = paid.toPlainString(),
                        remainingAmount = expected.subtract(paid).toPlainString(),
                        usedModes = modes
                    )
                else ->
                    PaymentRecoveryAssessment.Conflicting(
                        "Recorded payment ${paid.toPlainString()} exceeds bill total ${expected.toPlainString()}."
                    )
            }
        } catch (cause: IllegalArgumentException) {
            PaymentRecoveryAssessment.Conflicting(
                cause.message ?: "Payment records are invalid."
            )
        }
    }

    private fun parseMoney(value: String, label: String): BigDecimal {
        val parsed = value.trim().toBigDecimalOrNull()
            ?: throw IllegalArgumentException("$label amount is invalid.")
        require(parsed.scale() <= moneyScale) { "$label amount supports at most two decimal places." }
        return parsed.setScale(moneyScale, RoundingMode.UNNECESSARY)
    }
}
