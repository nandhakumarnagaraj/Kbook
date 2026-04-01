package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "token_blocklist")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenBlocklist {

    @Id
    @Column(name = "jti", length = 64, nullable = false)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Long expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Long revokedAt;
}
