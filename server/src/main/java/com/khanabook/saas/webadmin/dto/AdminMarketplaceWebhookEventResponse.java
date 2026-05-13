package com.khanabook.saas.webadmin.dto;

import lombok.Builder;

@Builder
public record AdminMarketplaceWebhookEventResponse(
        Long id,
        String platform,
        String platformOrderId,
        Boolean signatureValid,
        Boolean processed,
        Long receivedAt,
        String payload
) {
}
