package com.khanabook.saas.feature.notifications.data;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "email_outbox", uniqueConstraints = {
        @UniqueConstraint(name = "uq_email_outbox_revision", columnNames = {
                "report_type", "reference_key", "revision", "recipient"
        })
}, indexes = {
        @Index(name = "idx_email_outbox_pending", columnList = "status, next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    public static final String REPORT_TYPE_DAILY_EOD = "DAILY_EOD";
    public static final String REPORT_TYPE_SHIFT_CLOSE = "SHIFT_CLOSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "report_type", nullable = false, length = 50)
    private String reportType;

    @Column(name = "reference_key", nullable = false, length = 255)
    private String referenceKey;

    @Column(name = "revision", nullable = false)
    @Builder.Default
    private Integer revision = 1;

    @Column(name = "recipient", nullable = false, columnDefinition = "TEXT")
    private String recipient;

    @Column(name = "subject", nullable = false, columnDefinition = "TEXT")
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
    private String htmlBody;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 5;

    @Column(name = "next_attempt_at")
    private Long nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @Column(name = "sent_at")
    private Long sentAt;

    @PrePersist
    protected void onCreate() {
        long now = System.currentTimeMillis();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (nextAttemptAt == null) nextAttemptAt = now;
        if (status == null) status = STATUS_PENDING;
        if (attemptCount == null) attemptCount = 0;
        if (maxAttempts == null) maxAttempts = 5;
        if (revision == null) revision = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = System.currentTimeMillis();
    }
}
