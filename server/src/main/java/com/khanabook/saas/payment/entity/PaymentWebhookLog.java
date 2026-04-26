package com.khanabook.saas.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payment_webhook_logs", indexes = {
        @Index(name = "idx_payment_webhook_logs_payment", columnList = "payment_id"),
        @Index(name = "idx_payment_webhook_logs_txn", columnList = "txn_id")
})
@Getter
@Setter
public class PaymentWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 32)
    private PaymentGateway gateway;

    @Column(name = "txn_id", length = 128)
    private String txnId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Column(name = "processed", nullable = false)
    private Boolean processed;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;
}
