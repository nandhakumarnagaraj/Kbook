package com.khanabook.saas.feature.compliance.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * @deprecated Retired in V128. Online payment-assisted FSSAI renewals are retired.
 */
@Deprecated
@Getter
@Setter
public class FssaiRenewal {

    private Long id;
    private Long restaurantId;
    private String fssaiNumber;
    private Integer years;
    private BigDecimal amount;
    private String status = "PENDING";
    private String easebuzzTxnId;
    private Long createdAt;
    private Long updatedAt;
}
