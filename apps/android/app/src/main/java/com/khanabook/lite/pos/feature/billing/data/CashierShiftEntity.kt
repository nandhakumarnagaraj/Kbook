package com.khanabook.lite.pos.feature.billing.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * One cashier drawer session.
 *
 * Separate from the business day on purpose: the cashier who closes at 23:30 is closing a
 * shift, not sealing the day. Late orders until the cutoff (default 04:00) still belong to
 * the same business day and to a later shift.
 *
 * Authored on the device (a cashier with no network must still close), sealed on the
 * server, and then **append-only**: `CLOSED`/`AUTO_SEALED` never returns to `ACTIVE`.
 * That guard is what keeps last-write-wins from reopening a drawer that has already been
 * counted — the server mirrors `BillSyncService.protectBillState` for it.
 *
 * `expectedCash`, `cashVariance` and the totals are server-computed at seal; only
 * `declaredCash` is device truth. A terminal that could set its own expected figure could
 * report zero variance forever.
 *
 * Money is `TEXT` here and `NUMERIC(12,2)` on the server, matching the `bills` convention.
 */
@Entity(
    tableName = "local_cashier_shifts",
    indices = [
        Index(value = ["public_token"], unique = true),
        Index(value = ["restaurant_id", "terminal_id", "status"]),
        Index(value = ["restaurant_id", "business_date"])
    ]
)
data class CashierShiftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "restaurant_id", defaultValue = "0")
    val restaurantId: Long = 0,

    @ColumnInfo(name = "device_id", defaultValue = "''")
    val deviceId: String = "",

    @ColumnInfo(name = "terminal_id")
    val terminalId: String,

    @ColumnInfo(name = "terminal_series")
    val terminalSeries: String? = null,

    @ColumnInfo(name = "cashier_user_id")
    val cashierUserId: Long? = null,

    @ColumnInfo(name = "cashier_name")
    val cashierName: String? = null,

    /** `YYYY-MM-DD`, from `BusinessDate` — never a local `LocalDate.now()`. */
    @ColumnInfo(name = "business_date")
    val businessDate: String,

    @ColumnInfo(name = "opened_at")
    val openedAt: Long,

    @ColumnInfo(name = "closed_at")
    val closedAt: Long? = null,

    @ColumnInfo(name = "opening_float", defaultValue = "'0.00'")
    val openingFloat: String = "0.00",

    @ColumnInfo(name = "declared_cash")
    val declaredCash: String? = null,

    @ColumnInfo(name = "expected_cash")
    val expectedCash: String? = null,

    @ColumnInfo(name = "cash_variance")
    val cashVariance: String? = null,

    @ColumnInfo(name = "total_revenue")
    val totalRevenue: String? = null,

    @ColumnInfo(name = "total_orders")
    val totalOrders: Int? = null,

    @SerializedName("status")
    @ColumnInfo(name = "status", defaultValue = "'ACTIVE'")
    val status: String = STATUS_ACTIVE,

    @ColumnInfo(name = "close_source")
    val closeSource: String? = null,

    /** Rendered report as JSON, so a reprint is identical even if bills are edited later. */
    @ColumnInfo(name = "snapshot_json")
    val snapshotJson: String? = null,

    @SerializedName("publicToken")
    @ColumnInfo(name = "public_token")
    val publicToken: String,

    @SerializedName("serverId")
    @ColumnInfo(name = "server_id")
    val serverId: Long? = null,

    @SerializedName("serverUpdatedAt")
    @ColumnInfo(name = "server_updated_at", defaultValue = "0")
    val serverUpdatedAt: Long = 0L,

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false
) {
    val isOpen: Boolean get() = status == STATUS_ACTIVE

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_CLOSED = "CLOSED"
        const val STATUS_AUTO_SEALED = "AUTO_SEALED"

        const val SOURCE_CASHIER = "CASHIER"
        const val SOURCE_CUTOFF = "CUTOFF_CRON"

        /** Statuses a shift can never leave. */
        val SEALED_STATUSES = setOf(STATUS_CLOSED, STATUS_AUTO_SEALED)
    }
}
