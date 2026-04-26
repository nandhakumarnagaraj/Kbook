package com.khanabook.saas.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveRestaurantPaymentConfigRequest {

    @NotBlank
    private String merchantKey;

    @NotBlank
    private String salt;

    @NotBlank
    @Pattern(regexp = "^(TEST|PROD)$", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "environment must be TEST or PROD")
    private String environment;
}
