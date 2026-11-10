package com.khanabook.saas.feature.subscription.dto;

import com.khanabook.saas.feature.subscription.data.BillingCycle;
import com.khanabook.saas.feature.subscription.data.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSummaryDTO {
    private Long restaurantId;
    private String planCode;
    private String planName;
    private String planDescription;
    private SubscriptionStatus status;
    private BillingCycle billingCycle;
    private Long startedAt;
    private Long currentPeriodStart;
    private Long currentPeriodEnd;
    private Long trialEndAt;
    private Long gracePeriodEndAt;
    private Long daysRemaining;
    private Boolean isExpired;
    private Boolean isInGracePeriod;
    private Boolean autoRenew;

    // Quotas and usage
    private Integer maxTerminals;
    private Integer activeTerminalsCount;
    private Integer maxStaffMembers;
    private Integer activeStaffCount;

    // Feature gates
    private Boolean inventoryEnabled;
    private Boolean aiMenuEnabled;
    private Boolean onlinePaymentsEnabled;
    private Boolean advancedReportsEnabled;

    // Pricing
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;

    // Map of all feature entitlements for client convenience
    private Map<String, Boolean> features;

    private String notes;
}
