package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "otp_requests", indexes = {
    @Index(name = "ux_otp_requests_challenge_key", columnList = "challenge_key", unique = true),
    @Index(name = "idx_otp_requests_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_key", nullable = false, unique = true)
    private String challengeKey;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "otp", nullable = false, length = 10)
    private String otp;

    @Column(name = "expires_at", nullable = false)
    private Long expiresAt;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
