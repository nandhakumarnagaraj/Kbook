package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Push notification logic extracted from GenericSyncService.
 * Handles cancellation and payment received notifications after sync.
 */
@Service
@RequiredArgsConstructor
public class SyncNotificationService {
    private static final Logger log = LoggerFactory.getLogger(SyncNotificationService.class);

    private final BillRepository billRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PushNotificationService pushNotificationService;

    /**
     * Pushes cancellation notifications for bills that were successfully synced.
     */
    public void pushCancellationNotifications(
            Iterable<Bill> cancelledBills,
            Collection<Long> successfulLocalIds) {
        if (pushNotificationService == null) return;

        for (Bill bill : cancelledBills) {
            if (successfulLocalIds.contains(bill.getLocalId())) {
                try {
                    String displayOrder = bill.getDailyOrderDisplay() != null
                            ? bill.getDailyOrderDisplay()
                            : "#" + bill.getId();
                    pushNotificationService.pushToRestaurant(
                        bill.getRestaurantId(),
                        "Order Cancelled",
                        "Order " + displayOrder + " has been cancelled. Reason: "
                            + (bill.getCancelReason() != null ? bill.getCancelReason() : "None specified"),
                        "refund",
                        String.valueOf(bill.getId() != null ? bill.getId() : bill.getLocalId()),
                        "bill",
                        bill.getTotalAmount()
                    );
                } catch (Exception e) {
                    log.warn("Failed to push cancellation notification: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Pushes payment received notifications for new payments that were successfully synced.
     */
    public void pushPaymentNotifications(
            Iterable<BillPayment> newPayments,
            Collection<Long> successfulLocalIds) {
        if (pushNotificationService == null) return;

        for (BillPayment bp : newPayments) {
            if (successfulLocalIds.contains(bp.getLocalId())) {
                try {
                    String displayOrder = billRepository.findById(bp.getBillId())
                        .map(b -> b.getDailyOrderDisplay() != null
                                ? b.getDailyOrderDisplay()
                                : "#" + b.getId())
                        .orElse("#" + bp.getBillId());
                    String method = bp.getPaymentMode() != null ? bp.getPaymentMode() : "cash";
                    String amountStr = bp.getAmount() != null ? "₹" + bp.getAmount() : "";
                    pushNotificationService.pushToRestaurant(
                        bp.getRestaurantId(),
                        "Payment Received",
                        "Received " + amountStr + " for Order " + displayOrder + " via " + method,
                        "payment_received",
                        String.valueOf(bp.getBillId()),
                        "bill",
                        bp.getAmount()
                    );
                } catch (Exception e) {
                    log.warn("Failed to push sync payment notification: {}", e.getMessage());
                }
            }
        }
    }
}
