package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.PaymentMode

object PaymentGatewayHelper {

    fun isUpiSelection(mode: PaymentMode): Boolean =
        mode == PaymentMode.UPI ||
            mode == PaymentMode.PART_CASH_UPI ||
            mode == PaymentMode.PART_UPI_POS
}
