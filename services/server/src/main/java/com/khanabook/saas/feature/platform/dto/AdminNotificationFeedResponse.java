package com.khanabook.saas.feature.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationFeedResponse {
    private long totalCount;
    private long pushedCount;
    private long readCount;
    private long paymentCount;
    private long systemCount;
    private List<AdminNotificationItemResponse> notifications;
}
