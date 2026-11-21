package com.khanabook.lite.pos.feature.printing.domain

/**
 * Real-world health of a printer connection, combining reachability with
 * ESC/POS real-time status (paper, cover, error).
 *
 * Decoding follows the ESC/POS standard "DLE EOT" real-time status commands
 * (ASB not required): the printer replies with a single status byte.
 *
 *  - DLE EOT 1 → printer status (online/offline, drawer pin state)
 *  - DLE EOT 2 → offline cause status (cover open, paper end)
 *  - DLE EOT 3 → error cause status (mechanical / recoverable / unrecoverable)
 *  - DLE EOT 4 → paper roll sensor status
 *
 * Only n = 1, 3 and 4 are queried; cover-open therefore surfaces through n = 1
 * going offline rather than through a dedicated n = 2 read.
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
 * Each reply is a bitmap of flags, not an active-low "1 = fault" byte: bits that carry
 * a condition are SET when the condition holds (e.g. DLE EOT 1 bit 3 = offline), while
 * the bits marked "fixed" in the spec (1, 4) are simply always 1. Reading an inverted
 * flag as an error is what made a healthy printer report "Printer error" (BUG-033).
 */
object EscPosStatusDecoder {

    /**
     * Decodes DLE EOT 1 (printer status). Returns true when the printer reports a fault.
     *
     * Bit 3 = offline, bit 5 = waiting for online recovery (both mean "cannot print now").
     * Bit 2 is the drawer kick-out connector pin (0 = LOW, 1 = HIGH) and is NOT an error:
     * the all-clear reply 0x12 has it clear, because no cash drawer is wired to pin 3.
     */
    fun printerError(statusByte: Int): Boolean {
        return (statusByte and (PRINTER_OFFLINE or PRINTER_RECOVERY_WAIT)) != 0
    }

    /** DLE EOT 1 bit 3: 1 = offline. */
    private const val PRINTER_OFFLINE = 0x08

    /** DLE EOT 1 bit 5: 1 = waiting for online recovery after an error. */
    private const val PRINTER_RECOVERY_WAIT = 0x20

    /**
     * Decodes DLE EOT 4 (paper roll sensor status).
     * @return true when paper is present; false when paper end / near-end detected.
     */
    fun paperPresent(paperStatusByte: Int): Boolean {
        val paperEnd = (paperStatusByte and PAPER_END_MASK) == PAPER_END_MASK
        val paperLow = (paperStatusByte and PAPER_NEAR_END_MASK) == PAPER_NEAR_END_MASK
        return !paperEnd && !paperLow
    }

    /** DLE EOT 4 bits 2-3: 1,1 = paper roll end sensor active (the roll is empty). */
    private const val PAPER_END_MASK = 0x0C

    /** DLE EOT 4 bits 5-6: 1,1 = paper roll near-end sensor active (the roll is running low). */
    private const val PAPER_NEAR_END_MASK = 0x60

    /** @return true when paper roll is running low (near-end sensor). */
    fun paperLow(paperStatusByte: Int): Boolean =
        (paperStatusByte and PAPER_NEAR_END_MASK) == PAPER_NEAR_END_MASK &&
            !paperOut(paperStatusByte)

    /** @return true when the paper roll end sensor is active. */
    fun paperOut(paperStatusByte: Int): Boolean =
        (paperStatusByte and PAPER_END_MASK) == PAPER_END_MASK

    /**
     * Decodes DLE EOT 3 (error cause status).
     *
     * Bit 3 = mechanical error, bit 5 = auto-recoverable error, bit 6 = unrecoverable
     * error. Cover-open and paper-end are NOT reported here - they live in DLE EOT 2
     * (offline cause); this byte's bits 0-2 are undefined and must not be decoded.
     */
    fun fatalOrCoverError(errorStatusByte: Int): Boolean {
        return (errorStatusByte and ERROR_CAUSE_MASK) != 0
    }

    /** DLE EOT 3: bit 3 mechanical, bit 5 auto-recoverable, bit 6 unrecoverable. */
    private const val ERROR_CAUSE_MASK = 0x08 or 0x20 or 0x40

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
