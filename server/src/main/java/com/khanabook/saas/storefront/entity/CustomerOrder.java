package com.khanabook.saas.storefront.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customer_orders", indexes = {
        @Index(name = "idx_customer_orders_restaurant_created", columnList = "restaurant_id, created_at"),
        @Index(name = "idx_customer_orders_tracking_token", columnList = "tracking_token", unique = true)
})
@Getter
@Setter
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "public_order_code", nullable = false, length = 64, unique = true)
    private String publicOrderCode;

    @Column(name = "tracking_token", nullable = false, length = 64, unique = true)
    private String trackingToken;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "fulfillment_type", nullable = false, length = 32)
    private String fulfillmentType;

    @Column(name = "order_status", nullable = false, length = 32)
    private String orderStatus;

    @Column(name = "payment_status", nullable = false, length = 32)
    private String paymentStatus;

    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod;

    @Column(name = "source_channel", nullable = false, length = 32)
    private String sourceChannel;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "subtotal", nullable = false, columnDefinition = "NUMERIC(12,2)")
    private BigDecimal subtotal;

    @Column(name = "total_amount", nullable = false, columnDefinition = "NUMERIC(12,2)")
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
