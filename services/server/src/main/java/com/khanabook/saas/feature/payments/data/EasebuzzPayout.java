package com.khanabook.saas.feature.payments.data;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * @deprecated Retired in V129. Easebuzz payouts retired.
 */
@Deprecated
@Getter
@Setter
public class EasebuzzPayout {

    private Long id;
    private Long restaurantId;
    private String merchantRequestId;
    private String payoutId;
    private BigDecimal amount;
    private String status; // initiated, pending, success, failed
    private String beneficiaryName;
    private String accountNumber;
    private String ifsc;
    private String utr;
    private String errorMessage;
    private Long createdAt;
    private Long updatedAt;
}
