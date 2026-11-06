package com.khanabook.saas.feature.shifts.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Wire contract for a shift. Field names match the Android {@code CashierShiftSyncDto}
 * one-for-one; a field missing here is dropped silently on push (the 2026-09-27 failure
 * class), which is why {@code expectedCash} and the totals are present even though the
 * server recomputes and overwrites them at seal.
 */
@Data
public class CashierShiftDTO {

    @JsonProperty("serverId")
    private Long id;

    @JsonProperty("localId")
    private Long localId;

    private String deviceId;
    private Long restaurantId;
    private Long updatedAt;
    private Boolean isDeleted;
    private Long serverUpdatedAt;
    private Long createdAt;
    private Long version;

    private String terminalId;
    private String terminalSeries;
    private Long cashierUserId;
    private String cashierName;
    private LocalDate businessDate;
    private Long openedAt;
    private Long closedAt;
    private BigDecimal openingFloat;

    /** The only money field the device owns. */
    private BigDecimal declaredCash;

    /** Server-computed at seal; a client value is ignored on update. */
    private BigDecimal expectedCash;
    private BigDecimal cashVariance;
    private BigDecimal totalRevenue;
    private Integer totalOrders;

    private String status;
    private String closeSource;
    private String snapshotJson;
    private UUID publicToken;
}
