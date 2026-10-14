package com.khanabook.saas.feature.reports.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;

@Builder
public record AdminAuditEventResponse(
        String source,
        @JsonSerialize(using = ToStringSerializer.class)
        Long restaurantId,
        String shopName,
        String platform,
        String platformOrderId,
        String subMerchantId,
        String eventType,
        String rawStatus,
        Boolean signatureValid,
        Boolean processed,
        Long receivedAt,
        String payload
) {
}
