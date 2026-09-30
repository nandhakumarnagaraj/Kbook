package com.khanabook.saas.feature.business.dto;

/**
 * Acknowledgement that a fresh generated password was sent to a staff member.
 * Deliberately carries no password: the only copy the server keeps is the BCrypt
 * hash, and the plaintext exists solely long enough to be delivered over WhatsApp.
 */
public record StaffCredentialsResponse(
        Long userId,
        String phone
) {}
