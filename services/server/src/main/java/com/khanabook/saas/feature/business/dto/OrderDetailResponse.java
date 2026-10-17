package com.khanabook.saas.feature.business.dto;

import java.util.List;

public record OrderDetailResponse(
        BusinessOrderListItemResponse order,
        List<OrderLineItemResponse> lineItems
) {
}
