package com.khanabook.saas.core.utility;

/**
 * Canonicalizes phone numbers at every identity entry point (signup, OTP
 * request, login, staff creation, mobile-number update) so that format
 * variants ("+91XXXXXXXXXX", "0XXXXXXXXXX", "XXXXX XXXXX") can never become
 * distinct accounts — defense in depth behind the client-side normalization
 * the Android app performs.
 *
 * Rule (Indian market): strip non-digits, then keep the final 10 digits.
 *   +919450341518 -> 9450341518
 *   09450341518   -> 9450341518
 *   94503 41518   -> 9450341518
 * Inputs that cannot be reduced to 10 digits (already-clean 10-digit numbers,
 * emails used as login ids, garbage) are returned trimmed and unchanged so
 * exotic-but-legal values are never mangled.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            return digits.substring(digits.length() - 10);
        }
        return trimmed;
    }

    /** True when the value looks like a bare phone number (digits only, 10-13 chars). */
    public static boolean looksLikePhone(String value) {
        if (value == null) {
            return false;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.length() >= 10 && digits.length() <= 13 && digits.length() == value.trim().length();
    }
}
