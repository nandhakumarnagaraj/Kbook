package com.khanabook.saas.feature.business.dto;

public record StaffCreatedResponse(
        Long userId,
        String name,
        String phone,
        String role,
        boolean otpSent
) {}
