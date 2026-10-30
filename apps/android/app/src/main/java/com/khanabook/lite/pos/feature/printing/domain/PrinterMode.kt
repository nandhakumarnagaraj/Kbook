package com.khanabook.lite.pos.feature.printing.domain

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity

/**
 * How a terminal routes its two ticket types.
 *
 * [DUAL]   — customer receipt and kitchen KOT print on their own printers (the
 *            original layout; each role has its own `printer_profiles` row).
 * [SINGLE] — one physical printer prints both, kitchen KOT first then the
 *            receipt. The kitchen row is kept but is not used, so switching back
 *            to [DUAL] restores the shop's kitchen printer untouched.
 */
enum class PrinterMode {
    DUAL,
    SINGLE;

    companion object {
        fun fromValue(value: String?): PrinterMode = entries.firstOrNull { it.name == value } ?: DUAL
    }
}

fun RestaurantProfileEntity.printerModeValue(): PrinterMode = PrinterMode.fromValue(printerMode)

/**
 * The one printer that carries both tickets in [PrinterMode.SINGLE].
 *
 * Customer first: a shop normally configures the receipt printer, and the kitchen
 * printer is the fallback for a shop that only ever set up a kitchen device.
 * Callers must pass rows that are already enabled and have complete connection
 * details.
 */
fun mainPrinterOf(configured: List<PrinterProfileEntity>): PrinterProfileEntity? =
    configured.firstOrNull { it.role == PrinterRole.CUSTOMER.name }
        ?: configured.firstOrNull { it.role == PrinterRole.KITCHEN.name }

/**
 * This printer's connection, re-labelled as [role] — same device, same paper, but
 * the formatter treats it as the other ticket type.
 */
fun PrinterProfileEntity.forRole(role: PrinterRole, includeLogo: Boolean): PrinterProfileEntity =
    copy(role = role.name, includeLogo = includeLogo)
