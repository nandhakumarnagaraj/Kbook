package com.khanabook.saas.utility;

/**
 * App-wide constants. KhanaBook is an India-only product, so the default
 * timezone is fixed to India Standard Time. Centralised here so the value is
 * not scattered as string literals across services and controllers.
 */
public final class AppConstants {
    public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
    public static final String COUNTRY = "India";
    public static final String CURRENCY = "INR";

    private AppConstants() {
    }
}
