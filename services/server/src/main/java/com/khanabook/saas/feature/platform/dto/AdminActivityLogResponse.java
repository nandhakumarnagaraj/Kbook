package com.khanabook.saas.feature.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminActivityLogResponse {
    private Long id;
    private Long restaurantId;
    private String shopName;
    private String userId;
    private String action;
    private String outcome;
    private String targetCanonicalId;
    private Long createdAt;
}
