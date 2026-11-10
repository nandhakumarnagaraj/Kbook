package com.khanabook.saas.feature.subscription.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurant_subscriptions")
@Getter
@Setter
public class RestaurantSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_code", referencedColumnName = "code", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 20, nullable = false)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "started_at", nullable = false)
    private Long startedAt;

    @Column(name = "current_period_start", nullable = false)
    private Long currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Long currentPeriodEnd;

    @Column(name = "trial_end_at")
    private Long trialEndAt;

    @Column(name = "grace_period_end_at")
    private Long gracePeriodEndAt;

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
