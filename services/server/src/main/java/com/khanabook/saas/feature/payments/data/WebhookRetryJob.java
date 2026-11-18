package com.khanabook.saas.feature.payments.data;

import lombok.Getter;
import lombok.Setter;

/**
 * @deprecated Retired in V129. Easebuzz webhook retry jobs retired.
 */
@Deprecated
@Getter
@Setter
public class WebhookRetryJob {

    private Long id;
    private String webhookType;
    private String jobKey;
    private String payload;
    private String status;
    private int attemptCount;
    private int maxAttempts;
    private long nextAttemptAt;
    private String lastError;
    private long createdAt;
    private long updatedAt;
}
