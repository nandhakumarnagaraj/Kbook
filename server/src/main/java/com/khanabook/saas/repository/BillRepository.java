package com.khanabook.saas.repository;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends SyncRepository<Bill, Long> {

    @Query("""
            SELECT COALESCE(MAX(b.invoiceSequence), 0)
            FROM Bill b
            WHERE b.restaurantId = :restaurantId
              AND b.terminalSeries = :terminalSeries
              AND b.financialYear = :financialYear
              AND b.isDeleted = false
            """)
    Long findMaxInvoiceSequence(
            @Param("restaurantId") Long restaurantId,
            @Param("terminalSeries") String terminalSeries,
            @Param("financialYear") String financialYear);

    // Every terminal must receive canonical server deltas, including bills it
    // originally created (for example admin refunds and immutable invoice state).
    @Query("SELECT b FROM Bill b WHERE b.restaurantId = :restaurantId " +
           "AND b.serverUpdatedAt > :lastSyncTimestamp")
    List<Bill> findUpdatedExcludingOwnActiveOnly(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("deviceId") String deviceId);

    @Query("SELECT b FROM Bill b WHERE b.restaurantId = :restaurantId " +
           "AND b.serverUpdatedAt > :lastSyncTimestamp")
    org.springframework.data.domain.Page<Bill> findUpdatedExcludingOwnActiveOnly(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("deviceId") String deviceId,
            org.springframework.data.domain.Pageable pageable);

    long countByIsDeletedFalse();

    List<Bill> findByRestaurantIdAndIsDeletedFalse(Long restaurantId);

    long countByRestaurantIdAndIsDeletedFalse(Long restaurantId);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b " +
           "WHERE b.isDeleted = false " +
           "AND LOWER(b.orderStatus) IN ('completed','paid') " +
           "AND LOWER(b.paymentStatus) IN ('success','paid')")
    BigDecimal sumCompletedRevenue();

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b " +
           "WHERE b.restaurantId = :restaurantId " +
           "AND b.isDeleted = false " +
           "AND LOWER(b.orderStatus) IN ('completed','paid') " +
           "AND LOWER(b.paymentStatus) IN ('success','paid')")
    BigDecimal sumCompletedRevenueByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COALESCE(SUM(b.refundAmount), 0) FROM Bill b WHERE b.isDeleted = false")
    BigDecimal sumRefundAmount();

    @Query("SELECT COALESCE(SUM(b.refundAmount), 0) FROM Bill b WHERE b.restaurantId = :restaurantId AND b.isDeleted = false")
    BigDecimal sumRefundAmountByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.refundAmount > 0")
    long countRefundedBills();

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.restaurantId = :restaurantId AND b.isDeleted = false AND b.refundAmount > 0")
    long countRefundedBillsByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT b.restaurantId, COUNT(b) FROM Bill b WHERE b.isDeleted = false GROUP BY b.restaurantId")
    List<Object[]> countGroupedByRestaurant();

    Optional<Bill> findByRestaurantIdAndPublicTokenAndIsDeletedFalse(Long restaurantId, java.util.UUID publicToken);

    Optional<Bill> findByRestaurantIdAndDeviceIdAndLocalIdAndIsDeletedFalse(
            Long restaurantId, String deviceId, Long localId);

    @Query("""
            SELECT b FROM Bill b
            WHERE b.restaurantId = :restaurantId
              AND b.isDeleted = false
              AND b.lastResetDate = :lastResetDate
              AND b.dailyOrderId = :dailyOrderId
              AND COALESCE(b.terminalSeries, '') = COALESCE(:terminalSeries, '')
              AND NOT (b.deviceId = :deviceId AND b.localId = :localId)
            """)
    Optional<Bill> findConflictingDailyOrder(
            @Param("restaurantId") Long restaurantId,
            @Param("lastResetDate") String lastResetDate,
            @Param("dailyOrderId") Long dailyOrderId,
            @Param("deviceId") String deviceId,
            @Param("localId") Long localId,
            @Param("terminalSeries") String terminalSeries);
}
