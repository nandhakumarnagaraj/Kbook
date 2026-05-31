package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_profiles", indexes = {
    @Index(name = "idx_customer_restaurant", columnList = "restaurant_id"),
    @Index(name = "idx_customer_phone_hash", columnList = "phone_hash")
})
@Getter
@Setter
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "phone_hash", nullable = false)
    private String phoneHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "total_orders", nullable = false)
    private long totalOrders;

    @Column(name = "total_spend", nullable = false, columnDefinition = "NUMERIC(12,2)")
    private BigDecimal totalSpend;

    @Column(name = "average_order_value", columnDefinition = "NUMERIC(12,2)")
    private BigDecimal averageOrderValue;

    @Column(name = "last_order_at")
    private Long lastOrderAt;

    @Column(name = "first_order_at")
    private Long firstOrderAt;

    @Column(name = "preferred_payment_mode")
    private String preferredPaymentMode;

    @Column(name = "segment")
    private String segment;

    @Column(name = "opted_out", nullable = false)
    private Boolean optedOut = false;

    @Column(name = "opted_out_at")
    private Long optedOutAt;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}