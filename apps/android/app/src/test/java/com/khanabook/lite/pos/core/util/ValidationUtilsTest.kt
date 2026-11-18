package com.khanabook.lite.pos.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationUtilsTest {

    // --- Password policy -----------------------------------------------------
    // ChangePasswordView now calls this same function, so these assertions cover the
    // rule the Change Password, Signup and Forgot Password screens all advertise.

    @Test
    fun `password requires eight characters, uppercase, digit and special`() {
        assertTrue(ValidationUtils.isValidPassword("Abcdef1!"))
        assertFalse(ValidationUtils.isValidPassword("Abcdef1"))   // 7 chars
        assertFalse(ValidationUtils.isValidPassword("abcdef1!"))  // no uppercase
        assertFalse(ValidationUtils.isValidPassword("Abcdefg!"))  // no digit
        assertFalse(ValidationUtils.isValidPassword("Abcdef12"))  // no special
        assertFalse(ValidationUtils.isValidPassword("abcdef"))
    }

    @Test
    fun `password strength message states the enforced rule`() {
        val message = ValidationUtils.passwordStrengthMessage()
        assertTrue(message.contains("8"))
        assertTrue(message.contains("uppercase", ignoreCase = true))
        assertTrue(message.contains("digit", ignoreCase = true))
        assertTrue(message.contains("special", ignoreCase = true))
    }

    // --- Decimal input sanitizer --------------------------------------------

    @Test
    fun `sanitizer keeps one decimal separator and the configured digits`() {
        assertEquals("12.5", ValidationUtils.sanitizeDecimalInput("12.5", 6, 2))
        assertEquals("12.5", ValidationUtils.sanitizeDecimalInput("12,5", 6, 2))
        assertEquals("123.56", ValidationUtils.sanitizeDecimalInput("1234.567", 3, 2))
        assertEquals("1.23", ValidationUtils.sanitizeDecimalInput("1.234", 6, 2))
    }

    @Test
    fun `sanitizer drops a second separator and non-numeric characters`() {
        assertEquals("12.56", ValidationUtils.sanitizeDecimalInput("12.5.6", 6, 2))
        assertEquals("", ValidationUtils.sanitizeDecimalInput("abc", 6, 2))
        assertEquals("12", ValidationUtils.sanitizeDecimalInput("1a2b", 6, 2))
    }

    @Test
    fun `sanitizer preserves a trailing separator while typing`() {
        assertEquals("12.", ValidationUtils.sanitizeDecimalInput("12.", 6, 2))
        assertEquals(".5", ValidationUtils.sanitizeDecimalInput(".5", 6, 2))
        assertEquals("0", ValidationUtils.sanitizeDecimalInput("0", 6, 2))
    }

    // --- Tax percentage ------------------------------------------------------

    @Test
    fun `tax percentage accepts whole and decimal values in range`() {
        assertTrue(ValidationUtils.isValidTaxPercentage("0"))
        assertTrue(ValidationUtils.isValidTaxPercentage("5"))
        assertTrue(ValidationUtils.isValidTaxPercentage("5.5"))
        assertTrue(ValidationUtils.isValidTaxPercentage("100"))
        assertFalse(ValidationUtils.isValidTaxPercentage("100.01"))
        assertFalse(ValidationUtils.isValidTaxPercentage("101"))
        assertFalse(ValidationUtils.isValidTaxPercentage(""))
        assertFalse(ValidationUtils.isValidTaxPercentage("abc"))
    }
}
