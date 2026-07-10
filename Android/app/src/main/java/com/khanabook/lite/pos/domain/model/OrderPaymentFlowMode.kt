package com.khanabook.lite.pos.domain.model

enum class OrderPaymentFlowMode(val dbValue: String, val displayLabel: String) {
    PAY_BEFORE_FOOD("pay_before_food", "Pay Before Food"),
    PAY_AFTER_FOOD("pay_after_food", "Pay After Food");

    companion object {
        fun fromDbValue(value: String?): OrderPaymentFlowMode =
            values().find { it.dbValue == value } ?: PAY_BEFORE_FOOD
    }
}
