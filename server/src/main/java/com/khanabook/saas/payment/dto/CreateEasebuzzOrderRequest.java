package com.khanabook.saas.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateEasebuzzOrderRequest {

    @NotNull
    private Long billId;

    @NotBlank
    private String paymentMethod;
}
