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
public class AdminBackupStatusResponse {
    private int totalSnapshots;
    private String latestBackup;
    private String databaseSize;
    private String retentionPolicy;
    private List<AdminBackupItemResponse> backups;
}
