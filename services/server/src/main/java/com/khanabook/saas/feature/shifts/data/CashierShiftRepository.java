package com.khanabook.saas.feature.shifts.data;

import com.khanabook.saas.feature.sync.data.SyncRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashierShiftRepository extends SyncRepository<CashierShift, Long> {

    /** Idempotent push identity: a retried shift must not create a second row. */
    Optional<CashierShift> findByRestaurantIdAndPublicTokenAndIsDeletedFalse(Long restaurantId, UUID publicToken);

    Optional<CashierShift> findByRestaurantIdAndTerminalIdAndStatusAndIsDeletedFalse(
            Long restaurantId, String terminalId, String status);

    List<CashierShift> findByRestaurantIdAndBusinessDateAndIsDeletedFalseOrderByOpenedAtAsc(
            Long restaurantId, LocalDate businessDate);
}
