package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "easebuzz_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_easebuzz_webhook_tenant_txn", columnNames = {"restaurant_id", "txn_id"})
}, indexes = {
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

    @Column(name = "txn_id", nullable = false, length = 64)
    private String txnId;

    @Column(name = "easebuzz_id", length = 64)
    private String easebuzzId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "amount", columnDefinition = "NUMERIC(12,2)")
    private java.math.BigDecimal amount;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;
}
