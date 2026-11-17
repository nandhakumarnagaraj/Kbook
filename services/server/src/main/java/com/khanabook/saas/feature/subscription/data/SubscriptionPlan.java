package com.khanabook.saas.feature.subscription.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SubscriptionPlan {
    private String code;
    private String name;
    private String description;
    private BigDecimal priceMonthly = BigDecimal.ZERO;
    private BigDecimal priceYearly = BigDecimal.ZERO;
    private Integer maxTerminals = 1;
    private Integer maxStaffMembers = 5;
    private Boolean inventoryEnabled = true;
    private Boolean aiMenuEnabled = false;
    private Boolean onlinePaymentsEnabled = true;
    private Boolean advancedReportsEnabled = true;
    private Boolean isActive = true;
    private Integer sortOrder = 0;
    private Long createdAt;
    private Long updatedAt;
}
