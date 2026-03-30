package com.khanabook.lite.pos.domain.util

object ValidationUtils {
    
    fun isValidPhone(phone: String): Boolean {
        return phone.matches(Regex("^\\d{10}$"))
    }

    fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return true
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidGst(gst: String): Boolean {
        if (gst.isBlank()) return false
        val gstRegex = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$".toRegex()
        return gst.matches(gstRegex)
    }

    fun isValidName(name: String): Boolean {
        return name.isNotBlank() && name.length >= 2
    }

    fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return hasUpper && hasDigit && hasSpecial
    }

    fun passwordStrengthMessage(): String =
        "Password must be at least 8 characters and contain uppercase, digit, and special character"

    fun isValidOtp(otp: String): Boolean {
        return otp.length == 6 && otp.all { it.isDigit() }
    }

    fun isValidTaxPercentage(percentage: String): Boolean {
        val value = percentage.toDoubleOrNull() ?: return false
        return value in 0.0..100.0
    }
}

