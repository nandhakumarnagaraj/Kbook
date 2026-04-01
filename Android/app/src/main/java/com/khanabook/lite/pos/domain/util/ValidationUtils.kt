package com.khanabook.lite.pos.domain.util

object ValidationUtils {

    // Compile regexes once to avoid repeated allocation in hot paths
    private val PHONE_REGEX = Regex("^\\d{10}$")
    private val GST_REGEX = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")

    fun isValidPhone(phone: String): Boolean = phone.matches(PHONE_REGEX)

    /** Returns true only for a non-blank, well-formed email address. */
    fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidGst(gst: String): Boolean {
        if (gst.isBlank()) return false
        return gst.matches(GST_REGEX)
    }

    fun isValidName(name: String): Boolean = name.isNotBlank() && name.length >= 2

    fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return hasUpper && hasDigit && hasSpecial
    }

    fun passwordStrengthMessage(): String =
        "Password must be at least 8 characters and contain uppercase, digit, and special character"

    fun isValidOtp(otp: String): Boolean = otp.length == 6 && otp.all { it.isDigit() }

    fun isValidTaxPercentage(percentage: String): Boolean {
        val value = percentage.toDoubleOrNull() ?: return false
        return value in 0.0..100.0
    }
}
