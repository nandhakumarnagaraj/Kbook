package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_token_hash", columnList = "tokenHash"),
    @Index(name = "idx_refresh_tokens_user_id", columnList = "userId"),
})
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "expires_at", nullable = false)
    private Long expiresAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(nullable = false)
    private boolean revoked = false;

    public RefreshToken(String tokenHash, Long userId, Long restaurantId, Long expiresAt, String deviceFingerprint) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.restaurantId = restaurantId;
        this.expiresAt = expiresAt;
        this.createdAt = System.currentTimeMillis();
        this.deviceFingerprint = deviceFingerprint;
        this.revoked = false;
    }
}
