package com.khanabook.saas.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurant_payment_config", uniqueConstraints = {
        @UniqueConstraint(name = "uq_restaurant_payment_config_restaurant_gateway",
                columnNames = {"restaurant_id", "gateway_name"})
}, indexes = {
        @Index(name = "idx_restaurant_payment_config_restaurant", columnList = "restaurant_id")
})
@Getter
@Setter
public class RestaurantPaymentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_name", nullable = false, length = 32)
    private PaymentGateway gatewayName;

    @Column(name = "merchant_key", nullable = false, length = 255)
    private String merchantKey;

    @Column(name = "encrypted_salt", nullable = false, columnDefinition = "TEXT")
    private String encryptedSalt;

    @Column(name = "environment", nullable = false, length = 16)
    private String environment;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
