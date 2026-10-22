package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.sync.service.GenericSyncService;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericSyncServerOwnedStateTest {

    @Test
    void deviceUserPushCannotReactivateStaffOrClearInvalidation() {
        User existing = new User();
        existing.setIsActive(false);
        existing.setTokenInvalidatedAt(12345L);
        existing.setRole(UserRole.SHOP_STAFF);

        User incoming = new User();
        incoming.setIsActive(true);
        incoming.setTokenInvalidatedAt(null);
        incoming.setRole(UserRole.OWNER);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsActive()).isFalse();
        assertThat(incoming.getTokenInvalidatedAt()).isEqualTo(12345L);
        assertThat(incoming.getRole()).isEqualTo(UserRole.SHOP_STAFF);
    }

    @Test
    void deviceProfilePushCannotUnsuspendBusiness() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setIsSuspended(true);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setIsSuspended(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsSuspended()).isTrue();
    }

    /**
     * Online Payments Setup is owner-gated in RestaurantPaymentConfigController only.
     * The flag rides on RestaurantProfileDTO, so a device push must not be able to flip
     * it on and bypass that check.
     */
    @Test
    void deviceProfilePushCannotEnableEasebuzzPayments() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setEasebuzzEnabled(false);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setEasebuzzEnabled(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getEasebuzzEnabled()).isFalse();
    }

    /**
     * Owner-side counterpart: an owner who legitimately enabled payments online must
     * survive a stale device push carrying the older disabled value.
     */
    @Test
    void deviceProfilePushCannotDisableOwnerEnabledEasebuzz() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setEasebuzzEnabled(true);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setEasebuzzEnabled(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getEasebuzzEnabled()).isTrue();
    }

    /**
     * paymentStatus "paid" is unreachable from the device vocabulary, so a payload carrying it
     * is not a device setting a payment — it is a client claiming a finalized state, and the
     * paid time on the row is the gateway's. The restore above only fires when the incoming
     * push is NOT paid, so before this scoped rule a payload that said "paid" kept whatever
     * paidAt its own clock produced, overwriting the value reconciliation works from.
     */
    @Test
    void gatewayPaidRowKeepsItsPaymentTimeEvenWhenThePushClaimsPaid() {
        Bill existing = paidBill(1_800_000_000_000L);
        Bill incoming = billWithPayment("paid", 1_800_000_999_000L);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getPaidAt()).isEqualTo(1_800_000_000_000L);
    }

    @Test
    void gatewayPaidRowKeepsItsPaymentTimeWhenPushIsNotPaid() {
        Bill existing = paidBill(1_800_000_000_000L);
        Bill incoming = billWithPayment("success", 1_800_000_999_000L);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getPaymentStatus()).isEqualTo("paid");
        assertThat(incoming.getPaidAt()).isEqualTo(1_800_000_000_000L);
    }

    /**
     * The other direction, so the guard above cannot be widened later without noticing: a bill
     * the server has never seen paid gets its paid time from the till that collected the cash.
     */
    @Test
    void unpaidRowLetsTheTillStampPaidAt() {
        Bill existing = billWithPayment("pending", null);
        Bill incoming = billWithPayment("success", 1_800_000_999_000L);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getPaidAt()).isEqualTo(1_800_000_999_000L);
    }

    private static Bill paidBill(long paidAt) {
        return billWithPayment("paid", paidAt);
    }

    private static Bill billWithPayment(String paymentStatus, Long paidAt) {
        Bill bill = new Bill();
        bill.setPaymentStatus(paymentStatus);
        bill.setPaidAt(paidAt);
        return bill;
    }

    @Test
    void deviceProfilePushPreservesEasebuzzWhenUnset() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setEasebuzzEnabled(true);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setEasebuzzEnabled(null);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getEasebuzzEnabled()).isTrue();
    }
}
