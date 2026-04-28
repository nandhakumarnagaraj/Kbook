package com.khanabook.saas.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateEasebuzzOrderRequest {

    @NotNull
    private Long billId;

    @NotBlank
    private String paymentMethod;

    /**
     * Optional. When the bill uses a part-payment mode (e.g. cash + UPI),
     * this is the portion to charge via Easebuzz. If null, the full bill
     * total is charged. Validated server-side: must be > 0 and <= bill total.
     */
    private BigDecimal gatewayAmount;
}
