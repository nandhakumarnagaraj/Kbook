package com.khanabook.saas.feature.inventory.data;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * @deprecated Retired in V127. Customer data is stored directly on bills.
 */
@Deprecated
@Getter
@Setter
public class CustomerProfile {

    private Long id;
    private Long restaurantId;
    private String phoneHash;
    private String displayName;
    private long totalOrders;
    private BigDecimal totalSpend = BigDecimal.ZERO;
    private BigDecimal averageOrderValue = BigDecimal.ZERO;
    private Long lastOrderAt;
    private Long firstOrderAt;
    private String preferredPaymentMode;
    private String segment;
    private Boolean optedOut = false;
    private Long optedOutAt;
    private Long createdAt;
    private Long updatedAt;
}
