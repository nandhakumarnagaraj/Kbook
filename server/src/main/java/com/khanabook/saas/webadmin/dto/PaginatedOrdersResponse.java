package com.khanabook.saas.webadmin.dto;

import java.util.List;

public record PaginatedOrdersResponse(
        List<BusinessOrderListItemResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
