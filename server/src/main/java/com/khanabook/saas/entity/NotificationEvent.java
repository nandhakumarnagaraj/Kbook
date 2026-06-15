package com.khanabook.saas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "notification_events", indexes = {
    @Index(name = "idx_notification_events_restaurant", columnList = "restaurant_id"),
    @Index(name = "idx_notification_events_read", columnList = "restaurant_id, is_read"),
    @Index(name = "idx_notification_events_type", columnList = "restaurant_id, notification_type")
})
@Getter
@Setter
public class NotificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "amount", columnDefinition = "NUMERIC(12,2)")
    private BigDecimal amount;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "is_pushed", nullable = false)
    private Boolean isPushed = false;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "read_at")
    private Long readAt;
}
