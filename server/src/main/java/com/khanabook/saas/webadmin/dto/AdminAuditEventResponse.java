package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

@Builder
public record AdminAuditEventResponse(
        String source,
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
