package com.khanabook.lite.pos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for the framework-free validation/pricing rules.
 * isValidEmail is intentionally not tested here — it uses android.util.Patterns
 * which needs Robolectric/instrumented context.
 */
class ValidationAndPricingTest {

    // ── ValidationUtils ────────────────────────────────────────────────────

    @Test
    fun `phone must be exactly 10 digits`() {
        assertTrue(ValidationUtils.isValidPhone("9876543210"))
        assertFalse(ValidationUtils.isValidPhone("98765"))
        assertFalse(ValidationUtils.isValidPhone("98765432101"))
        assertFalse(ValidationUtils.isValidPhone("98765abcde"))
        assertFalse(ValidationUtils.isValidPhone("+919876543210"))
    }

    @Test
    fun `gstin format is validated`() {
        assertTrue(ValidationUtils.isValidGst("29ABCDE1234F1Z5"))
        assertFalse(ValidationUtils.isValidGst(""))
        assertFalse(ValidationUtils.isValidGst("29ABCDE1234F1Z"))   // too short
        assertFalse(ValidationUtils.isValidGst("2AABCDE1234F1Z5"))  // first two must be digits
    }

    @Test
    fun `password requires length upper digit and special`() {
        assertTrue(ValidationUtils.isValidPassword("Abcdef1!"))
        assertFalse("too short", ValidationUtils.isValidPassword("Ab1!"))
        assertFalse("no upper", ValidationUtils.isValidPassword("abcdef1!"))
        assertFalse("no digit", ValidationUtils.isValidPassword("Abcdefg!"))
        assertFalse("no special", ValidationUtils.isValidPassword("Abcdefg1"))
    }

    @Test
    fun `otp must be 6 digits`() {
        assertTrue(ValidationUtils.isValidOtp("123456"))
        assertFalse(ValidationUtils.isValidOtp("12345"))
        assertFalse(ValidationUtils.isValidOtp("12345a"))
    }

    @Test
    fun `tax percentage between 0 and 100`() {
        assertTrue(ValidationUtils.isValidTaxPercentage("5"))
        assertTrue(ValidationUtils.isValidTaxPercentage("0"))
        assertTrue(ValidationUtils.isValidTaxPercentage("100"))
        assertFalse(ValidationUtils.isValidTaxPercentage("-1"))
        assertFalse(ValidationUtils.isValidTaxPercentage("101"))
        assertFalse(ValidationUtils.isValidTaxPercentage("abc"))
    }

    @Test
    fun `name requires at least two chars`() {
        assertTrue(ValidationUtils.isValidName("Al"))
        assertFalse(ValidationUtils.isValidName("A"))
        assertFalse(ValidationUtils.isValidName("  "))
    }

    // ── MenuPricingRules ───────────────────────────────────────────────────

    @Test
    fun `normalizePrice scales to two decimals`() {
        assertEquals("150.00", MenuPricingRules.normalizePrice("150"))
        assertEquals("150.56", MenuPricingRules.normalizePrice("150.555"))
        assertEquals("1.00", MenuPricingRules.normalizePrice("1"))
    }

    @Test
    fun `normalizePrice rejects out-of-band and invalid`() {
        assertThrows(IllegalArgumentException::class.java) {
            MenuPricingRules.normalizePrice("100000.01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuPricingRules.normalizePrice("abc")
        }
        // Rs.0 (and blank -> 0.00) is now rejected: min price is Rs.1, aligned with OCR.
        assertThrows(IllegalArgumentException::class.java) {
            MenuPricingRules.normalizePrice("0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MenuPricingRules.normalizePrice("")
        }
    }

    @Test
    fun `isValidPrice enforces Rs 1 floor aligned with OCR`() {
        assertFalse("zero no longer valid", MenuPricingRules.isValidPrice(0.0))
        assertFalse("below one rupee invalid", MenuPricingRules.isValidPrice(0.5))
        assertTrue(MenuPricingRules.isValidPrice(1.0))
        assertTrue(MenuPricingRules.isValidPrice(150.0))
        assertTrue(MenuPricingRules.isValidPrice(100000.0))
        assertFalse(MenuPricingRules.isValidPrice(100000.01))
        assertFalse(MenuPricingRules.isValidPrice(null))
    }
}
