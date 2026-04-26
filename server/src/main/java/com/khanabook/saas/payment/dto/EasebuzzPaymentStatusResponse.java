package com.khanabook.saas.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class EasebuzzPaymentStatusResponse {
    private Long billId;
    private Long paymentId;
    private String paymentStatus;
    private String gatewayTxnId;
    private BigDecimal amount;
    private String message;
}
