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

import java.math.BigDecimal;

@Entity
@Table(name = "easebuzz_webhook_events", indexes = {
    @Index(name = "idx_easebuzz_webhook_txn", columnList = "txn_id"),
    @Index(name = "idx_easebuzz_webhook_restaurant", columnList = "restaurant_id")
})
@Getter
@Setter
public class EasebuzzWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "txn_id", nullable = false)
    private String txnId;

    @Column(name = "easebuzz_id")
    private String easebuzzId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "amount", columnDefinition = "NUMERIC(12,2)")
    private BigDecimal amount;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;
}
