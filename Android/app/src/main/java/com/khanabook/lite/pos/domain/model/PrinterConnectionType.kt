package com.khanabook.lite.pos.domain.model

import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity

enum class PrinterConnectionType {
    BLUETOOTH,
    WIFI;

    companion object {
        fun fromValue(value: String?): PrinterConnectionType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BLUETOOTH
    }
}

fun PrinterProfileEntity.connectionTypeValue(): PrinterConnectionType =
    PrinterConnectionType.fromValue(connectionType)

fun PrinterProfileEntity.isConnectionConfigured(): Boolean =
    when (connectionTypeValue()) {
        PrinterConnectionType.BLUETOOTH -> macAddress.isNotBlank()
        PrinterConnectionType.WIFI -> !host.isNullOrBlank() && port in 1..65535
    }

fun PrinterProfileEntity.connectionTargetKey(): String =
    when (connectionTypeValue()) {
        PrinterConnectionType.BLUETOOTH -> macAddress
        PrinterConnectionType.WIFI -> "wifi:${host.orEmpty()}:$port"
    }
