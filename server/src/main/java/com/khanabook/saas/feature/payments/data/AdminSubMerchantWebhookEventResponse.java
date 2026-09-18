package com.khanabook.saas.feature.payments.data;

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
