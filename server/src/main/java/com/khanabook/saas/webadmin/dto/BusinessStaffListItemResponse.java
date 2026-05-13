package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

@Builder
public record BusinessStaffListItemResponse(
        Long userId,
        String name,
        String loginId,
        String email,
        String whatsappNumber,
        String role,
        boolean active,
        Long updatedAt
) {
}
