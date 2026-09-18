package com.khanabook.lite.pos.domain.model


enum class PaymentMode(val dbValue: String, val displayLabel: String) {
    CASH("cash", "Cash"),
    UPI("upi", "UPI"),
    POS("pos", "POS Machine"),
    EASEBUZZ("easebuzz", "Pay Online"),
    PAYMENT_LINK("payment_link", "Send Payment Link"),
    PART_CASH_UPI("part_cash_upi", "Cash + UPI"),
    PART_CASH_POS("part_cash_pos", "Cash + POS"),
    PART_UPI_POS("part_upi_pos", "UPI + POS");

    companion object {
        fun fromDbValue(value: String?): PaymentMode =
            if (value == null) CASH else values().find { 
                it.dbValue.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) 
            } ?: CASH
    }
}

enum class OrderStatus(val dbValue: String) {
    DRAFT("draft"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    companion object {
        fun fromDbValue(value: String?): OrderStatus = 
            if (value == null) DRAFT else values().find { 
                it.dbValue.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) 
            } ?: DRAFT
    }
}

enum class PaymentStatus(val dbValue: String) {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed");

    companion object {
        fun fromDbValue(value: String?): PaymentStatus = 
            if (value == null) FAILED else values().find { 
                it.dbValue.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) 
            } ?: FAILED
    }
}

enum class FoodType(val dbValue: String, val displayLabel: String) {
    VEG("veg", "Veg"),
    NON_VEG("nonveg", "Non-Veg");

    companion object {
        fun fromDbValue(value: String): FoodType = 
            values().find { it.dbValue == value } ?: VEG
    }
}


