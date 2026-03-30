package com.khanabook.lite.pos.domain.model


enum class PaymentMode(val dbValue: String, val displayLabel: String) {
    CASH("cash", "Cash"),
    UPI("upi", "UPI"),
    POS("pos", "POS Machine"),
    ZOMATO("zomato", "Zomato"),
    SWIGGY("swiggy", "Swiggy"),
    OWN_WEBSITE("own_website", "Own Website"),
    PART_CASH_UPI("part_cash_upi", "Cash + UPI"),
    PART_CASH_POS("part_cash_pos", "Cash + POS"),
    PART_UPI_POS("part_upi_pos", "UPI + POS");

    companion object {
        fun fromDbValue(value: String): PaymentMode = 
            values().find { it.dbValue == value } ?: CASH
    }
}

enum class OrderStatus(val dbValue: String) {
    DRAFT("draft"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    companion object {
        fun fromDbValue(value: String): OrderStatus = 
            values().find { it.dbValue == value } ?: DRAFT
    }
}

enum class PaymentStatus(val dbValue: String) {
    SUCCESS("success"),
    FAILED("failed");

    companion object {
        fun fromDbValue(value: String): PaymentStatus = 
            values().find { it.dbValue == value } ?: FAILED
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


