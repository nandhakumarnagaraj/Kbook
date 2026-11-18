package com.khanabook.saas.feature.payments.data;

import lombok.Getter;
import lombok.Setter;

/**
 * @deprecated Retired in V129. Easebuzz sub-merchant webhook events retired.
 */
@Deprecated
@Getter
@Setter
public class EasebuzzSubMerchantWebhookEvent {

    private Long id;
    private String subMerchantId;
    private String eventType;
    private String rawStatus;
    private String payload;
    private Boolean processed = false;
    private Long receivedAt;
}
