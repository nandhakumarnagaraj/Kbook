package com.khanabook.saas.feature.business.dto;

import java.util.List;

public record PaginatedOrdersResponse(
        List<BusinessOrderListItemResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        Double totalRevenue,
        Long completedCount,
        Long cancelledCount
) {
    public PaginatedOrdersResponse(
            List<BusinessOrderListItemResponse> content,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {
        this(content, totalElements, totalPages, page, size, null, null, null);
    }
}
