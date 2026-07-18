package com.khanabook.saas.webadmin.dto;

public record StaffCreatedResponse(
        Long userId,
        String name,
        String phone,
        String role,
        String temporaryPassword
) {}
