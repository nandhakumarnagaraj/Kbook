package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "easebuzz_sub_merchant_webhook_event", indexes = {
    @Index(name = "idx_sub_merchant_webhook_event_sub", columnList = "sub_merchant_id"),
    @Index(name = "idx_sub_merchant_webhook_event_processed", columnList = "processed")
})
@Getter
@Setter
public class EasebuzzSubMerchantWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sub_merchant_id", nullable = false)
    private String subMerchantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "raw_status")
    private String rawStatus;

    @Column(name = "payload", columnDefinition = "JSONB", nullable = false)
    private String payload;

    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;
}
