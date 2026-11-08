package com.khanabook.saas.feature.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBackupItemResponse {
    private String id;
    private String timestamp;
    private String type;
    private String size;
    private String status;
    private String location;
}
