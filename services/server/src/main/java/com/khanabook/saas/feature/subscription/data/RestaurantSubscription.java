package com.khanabook.saas.feature.subscription.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantSubscription {
    private Long id;
    private Long restaurantId;
    private SubscriptionPlan plan;
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    private BillingCycle billingCycle = BillingCycle.MONTHLY;
    private Long startedAt;
    private Long currentPeriodStart;
    private Long currentPeriodEnd;
    private Long trialEndAt;
    private Long gracePeriodEndAt;
    private Boolean autoRenew = true;
    private String notes;
    private Long createdAt;
    private Long updatedAt;
    private Long version = 0L;
}
