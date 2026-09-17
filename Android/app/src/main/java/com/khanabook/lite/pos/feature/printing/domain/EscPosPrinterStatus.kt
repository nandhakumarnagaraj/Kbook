package com.khanabook.lite.pos.feature.printing.domain

/**
 * Real-world health of a printer connection, combining reachability with
 * ESC/POS real-time status (paper, cover, error).
 *
 * Decoding follows the ESC/POS standard "DLE EOT" real-time status commands
 * (ASB not required): the printer replies with a single status byte.
 *
 *  - DLE EOT 1 → printer status
 *  - DLE EOT 2 → drawer kick-out connector status
 *  - DLE EOT 3 → error status
 *  - DLE EOT 4 → paper roll sensor status
 */
enum class PrinterHealth {
    /** Socket reachable AND printer reports paper present, no errors. */
    HEALTHY,

    /** Socket reachable but paper is running low (near-end sensor active). */
    PAPER_LOW,

    /** Socket reachable but paper is out (end sensor active) — prints will not appear. */
    PAPER_OUT,

    /** Socket reachable but printer reports an error (cover open, cutter jam, etc.). */
    ERROR,

    /** Socket could not be established at all. */
    UNREACHABLE,

    /** Printer reachable but does not answer status queries (cheap/legacy models). */
    UNKNOWN;

    val isReachable: Boolean
        get() = this != UNREACHABLE

    val isPrintable: Boolean
        get() = this == HEALTHY || this == UNKNOWN
}

/**
 * Decodes ESC/POS DLE EOT status bytes.
 *
 * All bytes are "active-low" for their designated bits: a 1 in the documented
 * bit position means the condition is FALSE (per ESC/POS spec the bits are
 * defined as "0 = condition true" for most statuses).
 */
object EscPosStatusDecoder {

    /** Decodes DLE EOT 1 (printer status). Returns true when the printer reports an error. */
    fun printerError(statusByte: Int): Boolean {
        // Bit 2 (0x04): 1 = no error... actually spec: bit2=0 → cover open / paper end / error occurred
        // Spec: bit 2 = 0 means "an error has occurred" style encoding: status byte bit 2 == 0 → error.
        return (statusByte and 0x04) == 0
    }

    /**
     * Decodes DLE EOT 4 (paper roll sensor status).
     * @return true when paper is present; false when paper end / near-end detected.
     */
    fun paperPresent(paperStatusByte: Int): Boolean {
        val paperEnd = (paperStatusByte and 0x60) == 0x60 // bits 5,6 set → paper roll end
        val paperLow = (paperStatusByte and 0x0C) == 0x0C // bits 2,3 set → paper near-end
        return !paperEnd && !paperLow
    }

    /** @return true when paper roll is running low (near-end sensor). */
    fun paperLow(paperStatusByte: Int): Boolean =
        (paperStatusByte and 0x0C) == 0x0C && !paperOut(paperStatusByte)

    /** @return true when the paper roll end sensor is active. */
    fun paperOut(paperStatusByte: Int): Boolean =
        (paperStatusByte and 0x60) == 0x60

    /** Decodes DLE EOT 3 (error status): cover open, paper end by error, recoverable error. */
    fun fatalOrCoverError(errorStatusByte: Int): Boolean {
        val coverOpen = (errorStatusByte and 0x04) == 0x04  // bit 2: 1 = cover open
        val paperEndErr = (errorStatusByte and 0x08) == 0x08 // bit 3: 1 = paper end
        val unrecoverable = (errorStatusByte and 0x20) == 0x20 // bit 5: 1 = unrecoverable error
        return coverOpen || paperEndErr || unrecoverable
    }

    /**
     * Builds a [PrinterHealth] from the raw status bytes. Any null byte means the
     * printer did not answer that particular query (treated as UNKNOWN → best-effort).
     */
    fun fromStatusBytes(
        printerStatus: Int?,
        errorStatus: Int?,
        paperStatus: Int?
    ): PrinterHealth {
        // Paper-out detection first — most actionable for a POS.
        if (paperStatus != null && paperOut(paperStatus)) return PrinterHealth.PAPER_OUT
        if (errorStatus != null && fatalOrCoverError(errorStatus)) {
            return if (paperStatus != null && paperLow(paperStatus)) {
                PrinterHealth.PAPER_LOW
            } else {
                PrinterHealth.ERROR
            }
        }
        if (paperStatus != null && paperLow(paperStatus)) return PrinterHealth.PAPER_LOW
        if (printerStatus != null && printerError(printerStatus)) return PrinterHealth.ERROR
        // If nothing answered at all, the printer likely does not support DLE EOT.
        return if (printerStatus == null && errorStatus == null && paperStatus == null) {
            PrinterHealth.UNKNOWN
        } else {
            PrinterHealth.HEALTHY
        }
    }

    /** Raw ESC/POS real-time status request bytes (DLE EOT n). */
    val CMD_PRINTER_STATUS: ByteArray = byteArrayOf(0x10, 0x04, 0x01)
    val CMD_ERROR_STATUS: ByteArray = byteArrayOf(0x10, 0x04, 0x03)
    val CMD_PAPER_STATUS: ByteArray = byteArrayOf(0x10, 0x04, 0x04)
}
