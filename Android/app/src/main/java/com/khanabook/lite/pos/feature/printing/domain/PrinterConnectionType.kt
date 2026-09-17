package com.khanabook.lite.pos.feature.printing.domain

import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity

enum class PrinterConnectionType {
    BLUETOOTH,
    WIFI,
    USB;

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
        // USB printers are keyed by vendorId:productId:serial. Serial may be
        // blank on cheap models — then the key degrades to VID:PID + device name.
        PrinterConnectionType.USB -> !macAddress.isNullOrBlank() &&
            macAddress.startsWith("usb:")
    }

fun PrinterProfileEntity.connectionTargetKey(): String =
    when (connectionTypeValue()) {
        PrinterConnectionType.BLUETOOTH -> macAddress
        PrinterConnectionType.WIFI -> "wifi:${host.orEmpty()}:$port"
        PrinterConnectionType.USB -> macAddress
    }
