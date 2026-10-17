package com.khanabook.saas;

import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.platform.dto.AdminBusinessListItemResponse;
import com.khanabook.saas.feature.platform.service.AdminReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Last Used" on the admin Businesses table has to mean the last bill the shop actually
 * SETTLED — not the last time the till wrote a row.
 *
 * <p>It used to be {@code MAX(created_at)} over every non-deleted bill, so a still-open
 * order (or a cancelled one) made a shop look active, and the column contradicted the
 * Revenue figure built from the same table with a stricter bar. These tests pin the
 * stricter bar, which now matches {@code BillRepository.sumCompletedRevenue()}: a bill
 * counts only when it is both closed ({@code order_status} completed/paid) and paid
 * ({@code payment_status} success/paid).
 *
 * <p>Each case uses its own restaurantId because the H2 schema is shared across the
 * cached test context, and drives the real {@link AdminReadService#getBusinesses()} so
 * the assertion lands on the value the admin table actually renders.
 */
class AdminLastUsedSettledBillsTest extends BaseIntegrationTest {

    private static final long TENANT_SETTLED_THEN_OPEN = 7_710_001L;
    private static final long TENANT_ONLY_OPEN = 7_710_002L;
    private static final long TENANT_CANCELLED_AFTER_SETTLED = 7_710_003L;

    private static final long SETTLED_AT = 1_800_000_000_000L;
    private static final long LATER = SETTLED_AT + 3_600_000L;

    @Autowired private BillRepository billRepository;
    @Autowired private AdminReadService adminReadService;

    @Test
    @DisplayName("a newer still-open bill does not push Last Used forward")
    void openBillDoesNotCountAsUsage() {
        persistUser("owner-7710001", TENANT_SETTLED_THEN_OPEN, UserRole.OWNER);
        billRepository.save(bill(TENANT_SETTLED_THEN_OPEN, "completed", "success", SETTLED_AT));
        // Newest row on the tenant — but nobody has paid for it yet.
        billRepository.save(bill(TENANT_SETTLED_THEN_OPEN, "open", "pending", LATER));

        assertThat(lastBillTimeFor(TENANT_SETTLED_THEN_OPEN)).isEqualTo(SETTLED_AT);
        assertThat(lastUsedAtFor(TENANT_SETTLED_THEN_OPEN)).isEqualTo(SETTLED_AT);
    }

    @Test
    @DisplayName("a business with only unsettled bills reports no usage at all")
    void onlyOpenBillsReportNoUsage() {
        persistUser("owner-7710002", TENANT_ONLY_OPEN, UserRole.OWNER);
        billRepository.save(bill(TENANT_ONLY_OPEN, "open", "pending", SETTLED_AT));

        assertThat(lastBillTimeFor(TENANT_ONLY_OPEN)).isNull();
        assertThat(lastUsedAtFor(TENANT_ONLY_OPEN)).isNull();
    }

    @Test
    @DisplayName("a voided bill does not count, and 'paid' is accepted like 'success'")
    void cancelledBillDoesNotCountAsUsage() {
        persistUser("owner-7710003", TENANT_CANCELLED_AFTER_SETTLED, UserRole.OWNER);
        // Older, but actually paid — and spelled the other way payment_status is stored.
        billRepository.save(bill(TENANT_CANCELLED_AFTER_SETTLED, "paid", "paid", SETTLED_AT));
        billRepository.save(bill(TENANT_CANCELLED_AFTER_SETTLED, "cancelled", "cancelled", LATER));

        assertThat(lastBillTimeFor(TENANT_CANCELLED_AFTER_SETTLED)).isEqualTo(SETTLED_AT);
        assertThat(lastUsedAtFor(TENANT_CANCELLED_AFTER_SETTLED)).isEqualTo(SETTLED_AT);
    }

    /** The grouped query's own answer for one tenant, or null when it reports no usage. */
    private Long lastBillTimeFor(long restaurantId) {
        return billRepository.lastBillTimeGroupedByRestaurant().stream()
                .filter(row -> restaurantId == ((Number) row[0]).longValue())
                .map(row -> ((Number) row[1]).longValue())
                .findFirst()
                .orElse(null);
    }

    /** What the admin Businesses table renders as Last Used for one business. */
    private Long lastUsedAtFor(long restaurantId) {
        // Pull the field out of the row AFTER resolving it. A null lastUsedAt is a
        // legitimate answer here, and Optional.map would fold it into "absent" — which
        // would make "this shop settled nothing" and "this shop is missing from the
        // directory" indistinguishable in a failure.
        AdminBusinessListItemResponse row = adminReadService.getBusinesses().stream()
                .filter(candidate -> candidate.restaurantId() != null
                        && candidate.restaurantId() == restaurantId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no business row for " + restaurantId));
        return row.lastUsedAt();
    }

    private Bill bill(long restaurantId, String orderStatus, String paymentStatus, long createdAt) {
        Bill bill = new Bill();
        bill.setLocalId(System.nanoTime());
        bill.setRestaurantId(restaurantId);
        bill.setDeviceId("test-device");
        bill.setPublicToken(UUID.randomUUID());
        bill.setDailyOrderId(1L);
        bill.setDailyOrderDisplay("D-1");
        bill.setOrderType("order");
        bill.setSubtotal(new BigDecimal("100.00"));
        bill.setTotalAmount(new BigDecimal("100.00"));
        bill.setPaymentMode("cash");
        bill.setOrderStatus(orderStatus);
        bill.setPaymentStatus(paymentStatus);
        bill.setLastResetDate("");
        bill.setIsDeleted(false);
        bill.setInventoryDeducted(false);
        bill.setCreatedAt(createdAt);
        bill.setUpdatedAt(createdAt);
        bill.setServerUpdatedAt(createdAt);
        return bill;
    }
}
