package com.khanabook.saas.feature.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {
    private String code;
    private String name;
    private String description;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private Integer maxTerminals;
    private Integer maxStaffMembers;
    private Boolean inventoryEnabled;
    private Boolean aiMenuEnabled;
    private Boolean onlinePaymentsEnabled;
    private Boolean advancedReportsEnabled;
    private Boolean isActive;
    private Integer sortOrder;
}
