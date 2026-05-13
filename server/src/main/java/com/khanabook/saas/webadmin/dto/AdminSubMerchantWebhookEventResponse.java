package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

@Builder
public record AdminSubMerchantWebhookEventResponse(
        Long id,
        String subMerchantId,
        String eventType,
        String rawStatus,
        Boolean processed,
        Long receivedAt,
        String payload
) {
}
