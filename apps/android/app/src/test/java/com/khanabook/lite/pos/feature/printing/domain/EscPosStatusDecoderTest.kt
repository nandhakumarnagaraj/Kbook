package com.khanabook.lite.pos.feature.printing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression table for DLE EOT decoding (BUG-033).
 *
 * The all-clear DLE EOT 1 reply is 0x12: bits 1 and 4 are the fixed 1 bits, the drawer pin
 * is LOW and the printer is online. The previous decoder read bit 2 as an inverted error
 * flag, so 0x12 came back as an error and every printer that answers DLE EOT was reported
 * as broken in Settings.
 */
class EscPosStatusDecoderTest {

    @Test
    fun `healthy printer status is not an error`() {
        assertFalse(EscPosStatusDecoder.printerError(0x12))
    }

    @Test
    fun `drawer pin high is not an error`() {
        // Bit 2 is the drawer kick-out connector pin, not a fault flag.
        assertFalse(EscPosStatusDecoder.printerError(0x16))
    }

    @Test
    fun `offline bit is an error`() {
        assertTrue(EscPosStatusDecoder.printerError(0x1A)) // 0x12 or 0x08
    }

    @Test
    fun `waiting for online recovery is an error`() {
        assertTrue(EscPosStatusDecoder.printerError(0x32)) // 0x12 or 0x20
    }

    @Test
    fun `undefined bits of the error-cause byte are ignored`() {
        // DLE EOT 3 bits 0-2 are undefined; 0x04 must not be decoded as cover-open.
        assertFalse(EscPosStatusDecoder.fatalOrCoverError(0x04))
        assertFalse(EscPosStatusDecoder.fatalOrCoverError(0x10)) // fixed bit alone
    }

    @Test
    fun `error cause bits are decoded`() {
        assertTrue(EscPosStatusDecoder.fatalOrCoverError(0x08)) // mechanical error
        assertTrue(EscPosStatusDecoder.fatalOrCoverError(0x20)) // auto-recoverable error
        assertTrue(EscPosStatusDecoder.fatalOrCoverError(0x40)) // unrecoverable error
    }

    @Test
    fun `all-clear replies produce HEALTHY`() {
        assertEquals(
            PrinterHealth.HEALTHY,
            EscPosStatusDecoder.fromStatusBytes(0x12, 0x10, 0x00)
        )
    }

    @Test
    fun `all-clear replies with the drawer pin high produce HEALTHY`() {
        assertEquals(
            PrinterHealth.HEALTHY,
            EscPosStatusDecoder.fromStatusBytes(0x16, 0x10, 0x00)
        )
    }

    @Test
    fun `paper end wins over a clean error byte`() {
        // DLE EOT 4 bits 2-3 (0x0C) are the paper roll end sensor.
        assertEquals(
            PrinterHealth.PAPER_OUT,
            EscPosStatusDecoder.fromStatusBytes(0x12, 0x10, 0x0C)
        )
    }

    @Test
    fun `near-end sensor reports paper low, not paper out`() {
        // DLE EOT 4 bits 5-6 (0x60) are the near-end sensor: the roll is low but still printing.
        assertTrue(EscPosStatusDecoder.paperLow(0x60))
        assertFalse(EscPosStatusDecoder.paperOut(0x60))
        assertEquals(
            PrinterHealth.PAPER_LOW,
            EscPosStatusDecoder.fromStatusBytes(0x12, 0x10, 0x60)
        )
    }

    @Test
    fun `end sensor reports paper out, not paper low`() {
        assertTrue(EscPosStatusDecoder.paperOut(0x0C))
        assertFalse(EscPosStatusDecoder.paperLow(0x0C))
        assertFalse(EscPosStatusDecoder.paperPresent(0x0C))
    }

    @Test
    fun `unrecoverable error produces ERROR`() {
        assertEquals(
            PrinterHealth.ERROR,
            EscPosStatusDecoder.fromStatusBytes(0x12, 0x50, 0x00)
        )
    }

    @Test
    fun `offline printer produces ERROR`() {
        assertEquals(
            PrinterHealth.ERROR,
            EscPosStatusDecoder.fromStatusBytes(0x1A, 0x10, 0x00)
        )
    }

    @Test
    fun `silent printer degrades to UNKNOWN`() {
        assertEquals(
            PrinterHealth.UNKNOWN,
            EscPosStatusDecoder.fromStatusBytes(null, null, null)
        )
    }

    @Test
    fun `printer answering only the status query is still printable`() {
        val health = EscPosStatusDecoder.fromStatusBytes(0x12, null, null)
        assertEquals(PrinterHealth.HEALTHY, health)
        assertTrue(health.isPrintable)
    }
}
