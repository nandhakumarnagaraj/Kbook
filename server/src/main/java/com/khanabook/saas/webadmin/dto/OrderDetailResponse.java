package com.khanabook.saas.webadmin.dto;

import java.util.List;

public record OrderDetailResponse(
        BusinessOrderListItemResponse order,
        List<OrderLineItemResponse> lineItems
) {
}
