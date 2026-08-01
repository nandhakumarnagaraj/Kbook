package com.khanabook.lite.pos.ui.feedback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuItemAddFeedbackTest {

    @Test
    fun `successful add plays feedback after cart mutation`() {
        val calls = mutableListOf<String>()

        performMenuItemAdd(
            addToCart = { calls += "add" },
            playFeedback = { calls += "sound" }
        )

        assertEquals(listOf("add", "sound"), calls)
    }

    @Test
    fun `failed add does not play feedback`() {
        var feedbackCount = 0

        assertThrows(IllegalStateException::class.java) {
            performMenuItemAdd(
                addToCart = { error("add failed") },
                playFeedback = { feedbackCount += 1 }
            )
        }

        assertEquals(0, feedbackCount)
    }

    @Test
    fun `generated menu sound is a deterministic mono pcm wave file`() {
        val first = createMenuItemAddWaveFile()
        val second = createMenuItemAddWaveFile()

        assertArrayEquals(first, second)
        assertEquals("RIFF", first.readAscii(0, 4))
        assertEquals("WAVE", first.readAscii(8, 4))
        assertEquals("fmt ", first.readAscii(12, 4))
        assertEquals("data", first.readAscii(36, 4))
        assertEquals(1, first.readShortLittleEndian(20))
        assertEquals(1, first.readShortLittleEndian(22))
        assertEquals(22_050, first.readIntLittleEndian(24))
        assertEquals(first.size - 44, first.readIntLittleEndian(40))
        assertTrue(first.size > 44)
    }

    private fun ByteArray.readAscii(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private fun ByteArray.readShortLittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readIntLittleEndian(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
}
