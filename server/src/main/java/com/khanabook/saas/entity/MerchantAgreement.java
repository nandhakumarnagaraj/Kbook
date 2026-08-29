package com.khanabook.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * KhanaBook &lt;-&gt; restaurant owner e-agreement (Sejda-signed PDF).
 *
 * <p>A KhanaBook-side legal record — deliberately NOT part of the Easebuzz
 * onboarding payload and NOT synced to Android devices. The signed PDF lives on
 * a private filesystem path (never under the public {@code /cdn/**} handler);
 * this row stores the storage key + signing metadata, not a public URL. Served
 * only through authenticated, tenant-scoped endpoints.
 */
@Entity
@Table(name = "merchant_agreement",
    uniqueConstraints = @UniqueConstraint(name = "ux_merchant_agreement_restaurant", columnNames = "restaurant_id"),
    indexes = @Index(name = "idx_merchant_agreement_restaurant", columnList = "restaurant_id"))
@Getter
@Setter
public class MerchantAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /** Relative path under the private docs base — never a public URL. */
    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Epoch millis when the agreement was signed/uploaded. */
    @Column(name = "signed_at")
    private Long signedAt;

    @Column(name = "signer_name")
    private String signerName;

    @Column(name = "agreement_version")
    private String agreementVersion;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
