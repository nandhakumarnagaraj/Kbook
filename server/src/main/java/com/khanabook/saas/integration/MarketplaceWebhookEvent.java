package com.khanabook.saas.integration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "marketplace_webhook_event", indexes = {
        @Index(name = "idx_marketplace_webhook_restaurant", columnList = "restaurant_id, platform, received_at")
})
@Getter
@Setter
public class MarketplaceWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "platform_order_id", length = 128)
    private String platformOrderId;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Column(name = "processed", nullable = false)
    private Boolean processed;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;
}
