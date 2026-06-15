package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "device_tokens", indexes = {
    @Index(name = "idx_device_tokens_restaurant", columnList = "restaurant_id"),
    @Index(name = "idx_device_tokens_token", columnList = "token")
})
@Getter
@Setter
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    @Column(name = "platform", length = 20)
    private String platform = "android";

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
