package com.khanabook.lite.pos.domain.model

enum class PrinterRole {
    CUSTOMER,
    KITCHEN;

    companion object {
        fun fromValue(value: String?): PrinterRole = entries.firstOrNull { it.name == value } ?: CUSTOMER
    }
}
