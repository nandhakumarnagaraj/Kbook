package com.khanabook.saas.feature.platform.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;

@Builder
public record AdminUserListItemResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String name,
        String email,
        String loginId,
        String phoneNumber,
        String role,
        @JsonSerialize(using = ToStringSerializer.class)
        Long restaurantId,
        String shopName,
        boolean isActive,
        Long createdAt,
        Long lastSeenAt
) {
}
