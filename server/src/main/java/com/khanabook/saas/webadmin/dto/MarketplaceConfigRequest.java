package com.khanabook.saas.webadmin.dto;

public record MarketplaceConfigRequest(
        String zomatoApiKey,
        String zomatoWebhookSecret,
        Boolean zomatoEnabled,
        String swiggyApiKey,
        String swiggyWebhookSecret,
        Boolean swiggyEnabled
) {
}
