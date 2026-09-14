package com.khanabook.lite.pos.feature.printing.domain

enum class PrinterRole {
    CUSTOMER,
    KITCHEN;

    companion object {
        fun fromValue(value: String?): PrinterRole = entries.firstOrNull { it.name == value } ?: CUSTOMER
    }
}
