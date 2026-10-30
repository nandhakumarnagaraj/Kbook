package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A child record (BillItem / BillPayment) that names an existing server bill must be
 * written only against a bill of the caller's own restaurant. Previously a bill that
 * did not exist, or that belonged to another tenant, was treated as "nothing to check"
 * and the child was accepted — letting one restaurant attach lines and payments to
 * another restaurant's bill.
 */
@ExtendWith(MockitoExtension.class)
class TerminalOwnershipServiceChildScopeTest {

    private static final Long CALLER_TENANT = 7L;
    private static final Long OTHER_TENANT = 99L;

    @Mock private RestaurantTerminalRepository terminalRepository;
    @Mock private BillRepository billRepository;
    @Mock private SecurityAuditService securityAuditService;

    @InjectMocks private TerminalOwnershipService service;

    @Test
    void billItemReferencingAnotherRestaurantsBill_isRejected() {
        Bill foreign = bill(OTHER_TENANT);
        when(billRepository.findById(1234L)).thenReturn(Optional.of(foreign));

        BillItem item = new BillItem();
        item.setLocalId(11L);
        item.setServerBillId(1234L);

        assertThat(service.isChildOwnershipAllowed(item, CALLER_TENANT, "T1", "dev-1", false))
                .isFalse();
        verify(securityAuditService).record(eq("SYNC_PUSH"), eq("CHILD_CROSS_TENANT"),
                eq(foreign.getPublicToken().toString()), eq(String.valueOf(CALLER_TENANT)));
    }

    @Test
    void billPaymentReferencingAnotherRestaurantsBill_isRejected() {
        when(billRepository.findById(4321L)).thenReturn(Optional.of(bill(OTHER_TENANT)));

        BillPayment payment = new BillPayment();
        payment.setLocalId(12L);
        payment.setServerBillId(4321L);

        assertThat(service.isChildOwnershipAllowed(payment, CALLER_TENANT, "T1", "dev-1", false))
                .isFalse();
    }

    @Test
    void childReferencingNonexistentBill_isRejected() {
        when(billRepository.findById(5555L)).thenReturn(Optional.empty());

        BillItem item = new BillItem();
        item.setLocalId(13L);
        item.setServerBillId(5555L);

        assertThat(service.isChildOwnershipAllowed(item, CALLER_TENANT, "T1", "dev-1", false))
                .isFalse();
    }

    @Test
    void childOfOwnBill_isAllowed() {
        when(billRepository.findById(42L)).thenReturn(Optional.of(bill(CALLER_TENANT)));

        BillItem item = new BillItem();
        item.setLocalId(14L);
        item.setServerBillId(42L);

        assertThat(service.isChildOwnershipAllowed(item, CALLER_TENANT, null, null, false))
                .isTrue();
    }

    @Test
    void childWithOnlyLocalBillReference_isAllowed() {
        // A bill created on the device and pushed in the same batch has no server id yet;
        // RelationalIdResolver maps it inside the caller's own tenant, so it stays allowed.
        BillItem item = new BillItem();
        item.setLocalId(15L);
        item.setBillId(3L);

        assertThat(service.isChildOwnershipAllowed(item, CALLER_TENANT, "T1", "dev-1", false))
                .isTrue();
        org.mockito.Mockito.verifyNoInteractions(billRepository);
    }

    private static Bill bill(Long restaurantId) {
        Bill bill = new Bill();
        bill.setRestaurantId(restaurantId);
        bill.setPublicToken(UUID.randomUUID());
        bill.setOrderStatus("draft");
        bill.setRefundAmount(BigDecimal.ZERO);
        return bill;
    }
}
