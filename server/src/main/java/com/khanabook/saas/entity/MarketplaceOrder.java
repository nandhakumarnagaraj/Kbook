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
@Table(name = "marketplace_orders", indexes = {
    @Index(name = "idx_marketplace_orders_restaurant", columnList = "restaurant_id, created_at"),
    @Index(name = "idx_marketplace_orders_bill", columnList = "bill_id"),
    @Index(name = "idx_marketplace_orders_platform", columnList = "platform, platform_order_id"),
    @Index(name = "idx_marketplace_orders_status", columnList = "restaurant_id, order_status")
})
@Getter
@Setter
public class MarketplaceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "bill_id")
    private Long billId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "platform_order_id", nullable = false)
    private String platformOrderId;

    @Column(name = "platform_status")
    private String platformStatus;

    @Column(name = "order_status", nullable = false)
    private String orderStatus = "pending";

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_address", columnDefinition = "TEXT")
    private String customerAddress;

    @Column(name = "subtotal", columnDefinition = "NUMERIC(12,2)")
    private java.math.BigDecimal subtotal;

    @Column(name = "tax_amount", columnDefinition = "NUMERIC(12,2)")
    private java.math.BigDecimal taxAmount;

    @Column(name = "total_amount", columnDefinition = "NUMERIC(12,2)", nullable = false)
    private java.math.BigDecimal totalAmount;

    @Column(name = "payment_mode")
    private String paymentMode;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "accepted_at")
    private Long acceptedAt;

    @Column(name = "rejected_at")
    private Long rejectedAt;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @Column(name = "ready_at")
    private Long readyAt;

    @Column(name = "completed_at")
    private Long completedAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @Column(name = "synced_at")
    private Long syncedAt;
}
