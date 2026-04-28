package com.khanabook.saas.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_restaurant_bill", columnList = "restaurant_id, bill_id"),
        @Index(name = "idx_payments_gateway_txn", columnList = "gateway_txn_id"),
        @Index(name = "idx_payments_gateway_payment", columnList = "gateway_payment_id")
})
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "amount", nullable = false, columnDefinition = "NUMERIC(12,2)")
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 32)
    private PaymentGateway gateway;

    @Column(name = "gateway_txn_id", nullable = false, length = 128)
    private String gatewayTxnId;

    @Column(name = "gateway_payment_id", length = 128)
    private String gatewayPaymentId;

    @Column(name = "gateway_status", length = 64)
    private String gatewayStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_mode", length = 32)
    private RefundMode refundMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 32)
    private RefundStatus refundStatus;

    @Column(name = "refund_amount", columnDefinition = "NUMERIC(12,2)")
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @Column(name = "merchant_refund_id", length = 128)
    private String merchantRefundId;

    @Column(name = "refund_gateway_refund_id", length = 128)
    private String refundGatewayRefundId;

    @Column(name = "refund_arn_number", length = 128)
    private String refundArnNumber;

    @Column(name = "refund_requested_at")
    private Long refundRequestedAt;

    @Column(name = "refund_processed_at")
    private Long refundProcessedAt;

    @Column(name = "refund_last_gateway_payload", columnDefinition = "TEXT")
    private String refundLastGatewayPayload;

    @Column(name = "refund_last_gateway_sync_at")
    private Long refundLastGatewaySyncAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @Column(name = "verified_at")
    private Long verifiedAt;
}
