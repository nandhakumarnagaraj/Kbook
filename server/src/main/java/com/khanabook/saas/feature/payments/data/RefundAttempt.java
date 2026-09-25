package com.khanabook.saas.feature.payments.data;

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
@Table(name = "easebuzz_refund_attempts", indexes = {
        @Index(name = "idx_easebuzz_refund_attempts_bill_status", columnList = "bill_id,status")
})
@Getter
@Setter
public class RefundAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "gateway_txn_id", nullable = false)
    private String gatewayTxnId;

    @Column(name = "easebuzz_payment_id", nullable = false)
    private String easebuzzPaymentId;

    @Column(name = "merchant_refund_id", nullable = false, unique = true)
    private String merchantRefundId;

    @Column(name = "gateway_refund_id", unique = true)
    private String gatewayRefundId;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
