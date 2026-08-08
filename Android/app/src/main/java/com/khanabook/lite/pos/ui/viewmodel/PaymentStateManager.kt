package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.data.local.entity.BillPaymentEntity
import com.khanabook.lite.pos.domain.manager.PaymentModeManager
import com.khanabook.lite.pos.domain.manager.PaymentRecoveryAssessment
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.domain.util.PaymentLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

/**
 * Manages payment mode selection, part amounts, gateway result state, and
 * payment validation. Extracted from BillingViewModel to isolate payment concerns.
 *
 * This class is NOT a ViewModel — it's a state holder owned by BillingViewModel.
 */
class PaymentStateManager {

    private val _paymentMode = MutableStateFlow(PaymentMode.UPI)
    val paymentMode: StateFlow<PaymentMode> = _paymentMode

    private val _partAmount1 = MutableStateFlow("0.0")
    val partAmount1: StateFlow<String> = _partAmount1

    private val _partAmount2 = MutableStateFlow("0.0")
    val partAmount2: StateFlow<String> = _partAmount2

    private val _gatewayTxnId = MutableStateFlow<String?>(null)
    val gatewayTxnId: StateFlow<String?> = _gatewayTxnId

    private val _gatewayStatus = MutableStateFlow<String?>(null)
    val gatewayStatus: StateFlow<String?> = _gatewayStatus

    private val _persistedPaymentTotal = MutableStateFlow<String?>(null)
    val persistedPaymentTotal: StateFlow<String?> = _persistedPaymentTotal

    private val _paymentRecovery = MutableStateFlow<PaymentRecoveryAssessment>(PaymentRecoveryAssessment.Empty)
    val paymentRecovery: StateFlow<PaymentRecoveryAssessment> = _paymentRecovery

    fun setPaymentMode(mode: PaymentMode, p1: String = "0.0", p2: String = "0.0") {
        if (mode != PaymentMode.UPI &&
            mode != PaymentMode.PART_CASH_UPI &&
            mode != PaymentMode.PART_UPI_POS
        ) {
            clearGatewayResult()
        }
        _paymentMode.value = mode
        _partAmount1.value = p1.ifBlank { "0.0" }
        _partAmount2.value = p2.ifBlank { "0.0" }
    }

    fun setPaymentModeOnly(mode: PaymentMode) {
        _paymentMode.value = mode
    }

    fun setPartAmounts(p1: String, p2: String) {
        _partAmount1.value = p1
        _partAmount2.value = p2
    }

    fun setGatewayResult(txnId: String?, status: String?) {
        _gatewayTxnId.value = txnId
        _gatewayStatus.value = status
    }

    fun clearGatewayResult() {
        _gatewayTxnId.value = null
        _gatewayStatus.value = null
    }

    fun setPersistedPaymentTotal(total: String?) {
        _persistedPaymentTotal.value = total
    }

    fun setPaymentRecovery(recovery: PaymentRecoveryAssessment) {
        _paymentRecovery.value = recovery
    }

    fun reset() {
        _paymentMode.value = PaymentMode.UPI
        _partAmount1.value = "0.0"
        _partAmount2.value = "0.0"
        _persistedPaymentTotal.value = null
        _paymentRecovery.value = PaymentRecoveryAssessment.Empty
        clearGatewayResult()
    }

    /**
     * Validates that UPI amounts don't exceed the per-transaction limit.
     * Returns null if valid, error message if invalid.
     */
    fun validatePaymentLimits(
        total: String,
        mode: PaymentMode = _paymentMode.value,
        partAmount1: String = _partAmount1.value,
        partAmount2: String = _partAmount2.value
    ): String? {
        val upiAmount = when (mode) {
            PaymentMode.UPI -> parseAmount(total)
            PaymentMode.PART_CASH_UPI -> parseAmount(partAmount2)
            PaymentMode.PART_UPI_POS -> parseAmount(partAmount1)
            else -> BigDecimal.ZERO
        }
        return if (upiAmount > PaymentLimits.UPI_SINGLE_TRANSACTION_MAX) {
            PaymentLimits.UPI_LIMIT_MESSAGE
        } else null
    }

    /**
     * Builds the list of BillPaymentEntity records for a given payment configuration.
     */
    fun buildPaymentEntities(
        billId: Long,
        paymentMode: PaymentMode = _paymentMode.value,
        totalAmount: String,
        partAmount1: String = _partAmount1.value,
        partAmount2: String = _partAmount2.value,
        operationBase: String,
        deviceId: String = "",
        restaurantId: Long = 0
    ): List<BillPaymentEntity> {
        return PaymentModeManager.getPaymentComponents(
            mode = paymentMode,
            totalAmount = totalAmount,
            partAmount1 = partAmount1,
            partAmount2 = partAmount2
        ).map { component ->
            BillPaymentEntity(
                billId = billId,
                paymentMode = component.mode.dbValue,
                amount = component.amount,
                operationId = "$operationBase:payment:${component.mode.dbValue}",
                deviceId = deviceId,
                restaurantId = restaurantId,
                verifiedBy = "manual"
            )
        }
    }

    private fun parseAmount(value: String): BigDecimal {
        return value.ifBlank { "0" }.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }
}
