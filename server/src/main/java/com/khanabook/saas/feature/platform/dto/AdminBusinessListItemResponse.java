package com.khanabook.saas.feature.platform.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@lombok.Builder
public record AdminBusinessListItemResponse(
        // Serialized as JSON string: 19-digit Snowflake-style restaurantIds
        // exceed JS Number.MAX_SAFE_INTEGER and lose precision as JSON numbers.
        @JsonSerialize(using = ToStringSerializer.class)
        Long restaurantId,
        String shopName,
        String ownerName,
        String ownerLoginId,
        String whatsappNumber,
        String email,
        boolean websiteEnabled,
        boolean isSuspended,
            long staffCount,
            long menuCount,
            long orderCount,
            Long updatedAt,
            // Joining date (profile createdAt) and last real activity (latest
            // non-deleted bill). Epoch millis — safely below JS MAX_SAFE_INTEGER.
            Long createdAt,
            Long lastUsedAt
) {
}
