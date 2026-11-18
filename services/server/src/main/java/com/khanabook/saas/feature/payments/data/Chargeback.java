package com.khanabook.saas.feature.payments.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * @deprecated Retired in V129. Easebuzz payment disputes retired.
 */
@Deprecated
@Getter
@Setter
public class Chargeback {

    private Long id;
    private Long restaurantId;
    private Long billId;
    private String easebuzzTxnId;
    private BigDecimal amount;
    private String reasonCode;
    private String reasonDescription;
    private String status;
    private BigDecimal fraudScore;
    private String evidenceUrl;
    private String customerName;
    private String customerPhone;
    private long createdAt;
    private Long resolvedAt;
}
