package com.khanabook.saas.feature.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationItemResponse {
    private Long id;
    private Long restaurantId;
    private String shopName;
    private String notificationType;
    private String title;
    private String message;
    private String referenceId;
    private String referenceType;
    private BigDecimal amount;
    private Boolean isRead;
    private Boolean isPushed;
    private Long createdAt;
}
