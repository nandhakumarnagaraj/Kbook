package com.khanabook.lite.pos.feature.payments.domain

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.PaymentMode

object PaymentGatewayHelper {

    fun isUpiSelection(mode: PaymentMode): Boolean =
        mode == PaymentMode.UPI ||
            mode == PaymentMode.PART_CASH_UPI ||
            mode == PaymentMode.PART_UPI_POS
}
