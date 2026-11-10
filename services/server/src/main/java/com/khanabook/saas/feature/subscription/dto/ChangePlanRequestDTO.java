package com.khanabook.saas.feature.subscription.dto;

import com.khanabook.saas.feature.subscription.data.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePlanRequestDTO {

    @NotBlank(message = "Plan code is required")
    private String planCode;

    private BillingCycle billingCycle = BillingCycle.MONTHLY;
}
