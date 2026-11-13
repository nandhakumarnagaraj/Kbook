package com.khanabook.saas.feature.auth.data;

/**
 * Read-only view of the owner's login identity.
 *
 * <p>The web-admin settings screen shows this instead of the editable
 * {@code RestaurantProfileDTO.whatsappNumber/email} fields: identity changes go
 * through the OTP-verified endpoints under {@code /business/profile/identity},
 * and {@code loginId} is what the owner actually types on the login screen, so
 * it must be visible without being editable.
 *
 * <p>{@code identityEditable} is false for Google-authenticated accounts: the
 * email is the Google account and the server refuses to change it.
 */
public record OwnerIdentityResponse(
        String loginId,
        String whatsappNumber,
        String email,
        String authProvider,
        boolean identityEditable) {
}
