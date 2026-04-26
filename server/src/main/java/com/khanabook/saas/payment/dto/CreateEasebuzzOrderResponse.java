package com.khanabook.saas.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CreateEasebuzzOrderResponse {
    private Long paymentId;
    private Long billId;
    private BigDecimal amount;
    private String currency;
    private String gateway;
    private String gatewayTxnId;
    private String checkoutUrl;
}
