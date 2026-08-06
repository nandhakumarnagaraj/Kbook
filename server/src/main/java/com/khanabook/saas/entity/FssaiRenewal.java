package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "fssai_renewals")
@Getter
@Setter
public class FssaiRenewal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "fssai_number", nullable = false)
    private String fssaiNumber;

    @Column(name = "years", nullable = false)
    private Integer years;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "easebuzz_txn_id", unique = true)
    private String easebuzzTxnId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
