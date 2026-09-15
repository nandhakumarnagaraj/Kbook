package com.khanabook.saas.sync.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class KotEventDTO {
    @JsonProperty("serverId")
    private Long id;
    private Long restaurantId;
    private String deviceId;
    private String terminalId;
    private String terminalSeries;
    private String publicToken;
    private String billPublicToken;
    private String kotRevision;
    private String eventType;
    private String itemSnapshotJson;
    private String originatingDeviceId;
    private String originTerminalId;
    private String originDeviceId;
    private String eventToken;
    private Long eventVersion;
    private Boolean isPrinted;
    private Long createdAt;
    private Long updatedAt;
}