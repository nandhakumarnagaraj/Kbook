package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "chargebacks")
@Getter
@Setter
public class Chargeback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "easebuzz_txn_id")
    private String easebuzzTxnId;

    @Column(name = "amount", nullable = false, columnDefinition = "NUMERIC(12,2)")
    private BigDecimal amount;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reason_description")
    private String reasonDescription;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "fraud_score", columnDefinition = "NUMERIC(5,2)")
    private BigDecimal fraudScore;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "resolved_at")
    private Long resolvedAt;
}