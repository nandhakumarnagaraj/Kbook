package com.khanabook.saas.feature.subscription.dto;

import com.khanabook.saas.feature.subscription.data.BillingCycle;
import com.khanabook.saas.feature.subscription.data.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSubscriptionUpdateRequestDTO {
    private String planCode;
    private SubscriptionStatus status;
    private BillingCycle billingCycle;
    private Integer extendDays;
    private Boolean autoRenew;
    private String notes;
}
