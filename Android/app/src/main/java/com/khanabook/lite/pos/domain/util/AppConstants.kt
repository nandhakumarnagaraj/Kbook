package com.khanabook.lite.pos.domain.util

/**
 * App-wide constants. KhanaBook is an India-only product, so the default
 * timezone is fixed to India Standard Time. Centralised here so the value is
 * not scattered as string literals across repositories, DAOs, and view models.
 */
object AppConstants {
    const val DEFAULT_TIMEZONE = "Asia/Kolkata"
    const val COUNTRY = "India"
    const val CURRENCY = "INR"
}
