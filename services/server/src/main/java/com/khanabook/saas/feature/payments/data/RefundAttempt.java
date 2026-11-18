package com.khanabook.saas.feature.payments.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * @deprecated Retired in V129. Easebuzz refund attempts retired.
 */
@Deprecated
@Getter
@Setter
public class RefundAttempt {

    private Long id;
    private Long billId;
    private Long restaurantId;
    private String gatewayTxnId;
    private String easebuzzPaymentId;
    private String merchantRefundId;
    private String gatewayRefundId;
    private BigDecimal amount;
    private String status;
    private String reason;
    private long createdAt;
    private long updatedAt;
}
