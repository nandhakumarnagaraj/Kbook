package com.khanabook.saas.feature.shifts.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.khanabook.saas.feature.sync.data.BaseSyncEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A cashier drawer session, authored on the device and sealed on the server.
 *
 * <p>Separate from the business day: the cashier who closes at 23:30 closes a shift, not
 * the day, and late orders until the cutoff still belong to the same business day.
 *
 * <p>Append-only once {@code CLOSED} or {@code AUTO_SEALED}. Last-write-wins must never
 * reopen a counted drawer — see {@code GenericSyncService.preserveServerOwnedState}.
 *
 * <p>Only {@code declared_cash} is device truth. {@code expected_cash},
 * {@code cash_variance} and the totals are computed server-side at seal, because a
 * terminal that could set its own expected figure could report zero variance forever.
 *
 * <p>The partial unique index {@code ux_cashier_shifts_one_active_per_terminal}
 * (V119) exists only in the database: it is a {@code WHERE status = 'ACTIVE'} index,
 * which JPA cannot express.
 */
@Entity
@Table(name = "cashier_shifts")
@Getter
@Setter
public class CashierShift extends BaseSyncEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_AUTO_SEALED = "AUTO_SEALED";

    @Column(name = "terminal_series")
    private String terminalSeries;

    @Column(name = "cashier_user_id")
    private Long cashierUserId;

    @Column(name = "cashier_name")
    private String cashierName;

    /** The business date this shift belongs to, from {@code BusinessDay}. */
    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "opened_at", nullable = false)
    private Long openedAt;

    @Column(name = "closed_at")
    private Long closedAt;

    @Column(name = "opening_float", nullable = false)
    private BigDecimal openingFloat = BigDecimal.ZERO;

    /** Device truth: the cash the cashier counted. */
    @Column(name = "declared_cash")
    private BigDecimal declaredCash;

    @Column(name = "expected_cash")
    private BigDecimal expectedCash;

    @Column(name = "cash_variance")
    private BigDecimal cashVariance;

    @Column(name = "total_revenue")
    private BigDecimal totalRevenue;

    @Column(name = "total_orders")
    private Integer totalOrders;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_ACTIVE;

    @Column(name = "close_source", length = 30)
    private String closeSource;

    /** Rendered report as JSON, so a reprint is identical even if bills change later. */
    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    /** Stable public identity; the device generates it so a retry is idempotent. */
    @JsonProperty("publicToken")
    @Column(name = "public_token", nullable = false)
    private UUID publicToken;

    public boolean isSealed() {
        return STATUS_CLOSED.equalsIgnoreCase(status) || STATUS_AUTO_SEALED.equalsIgnoreCase(status);
    }
}
