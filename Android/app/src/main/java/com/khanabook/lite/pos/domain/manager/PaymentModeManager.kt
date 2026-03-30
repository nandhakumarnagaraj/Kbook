package com.khanabook.lite.pos.domain.manager


import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.PaymentMode

object PaymentModeManager {

    fun getEnabledModes(profile: RestaurantProfileEntity): List<PaymentMode> {
        val modes = mutableListOf<PaymentMode>()
        if (profile.cashEnabled) modes.add(PaymentMode.CASH)
        if (profile.upiEnabled) modes.add(PaymentMode.UPI)
        if (profile.posEnabled) modes.add(PaymentMode.POS)
        if (profile.zomatoEnabled) modes.add(PaymentMode.ZOMATO)
        if (profile.swiggyEnabled) modes.add(PaymentMode.SWIGGY)
        if (profile.ownWebsiteEnabled) modes.add(PaymentMode.OWN_WEBSITE)

        
        if (profile.cashEnabled && profile.upiEnabled) modes.add(PaymentMode.PART_CASH_UPI)
        if (profile.cashEnabled && profile.posEnabled) modes.add(PaymentMode.PART_CASH_POS)
        if (profile.upiEnabled && profile.posEnabled) modes.add(PaymentMode.PART_UPI_POS)

        return modes
    }

    fun isPartPayment(mode: PaymentMode): Boolean {
        return mode == PaymentMode.PART_CASH_UPI || 
               mode == PaymentMode.PART_CASH_POS || 
               mode == PaymentMode.PART_UPI_POS
    }

    fun getPartLabels(mode: PaymentMode): Pair<String, String> {
        return when (mode) {
            PaymentMode.PART_CASH_UPI -> "Cash" to "UPI"
            PaymentMode.PART_CASH_POS -> "Cash" to "POS"
            PaymentMode.PART_UPI_POS -> "UPI" to "POS"
            else -> "" to ""
        }
    }

    fun getDisplayLabel(mode: PaymentMode): String {
        return mode.displayLabel
    }
}


