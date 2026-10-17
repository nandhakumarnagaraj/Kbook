package com.khanabook.saas.feature.business.dto;

public record StaffCreatedResponse(
        Long userId,
        String name,
        String phone,
        String role,
        /**
         * True when the generated password was handed to the WhatsApp number on
         * file. Renamed from {@code otpSent}: onboarding now delivers a generated
         * password, and the old name described a code that could never be redeemed.
         */
        boolean credentialsSent
) {}
