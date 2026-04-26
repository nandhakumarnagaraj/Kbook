package com.khanabook.saas.payment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RestaurantPaymentConfigResponse {
    private Long restaurantId;
    private String gateway;
    private String merchantKeyMasked;
    private String environment;
    private boolean active;
}
