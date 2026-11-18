package com.khanabook.saas.feature.payments.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * @deprecated Retired in V129. Easebuzz payment transaction webhook events retired.
 */
@Deprecated
@Getter
@Setter
public class EasebuzzWebhookEvent {

    private Long id;
    private Long restaurantId;
    private String txnId;
    private String easebuzzId;
    private String status;
    private BigDecimal amount;
    private String rawPayload;
    private Long receivedAt;
}
