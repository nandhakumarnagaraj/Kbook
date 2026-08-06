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

    // Restaurant-wide pull (admin / legacy clients without an X-Terminal-Token).
    // Returns every bill for the restaurant updated since lastSync, unfiltered by
    // terminal — i.e. NOT terminal-scoped. Never use this for a normal terminal pull;
    // terminal pulls must use findUpdatedForTerminal.
    @Query("SELECT b FROM Bill b WHERE b.restaurantId = :restaurantId " +
           "AND b.serverUpdatedAt > :lastSyncTimestamp")
    List<Bill> findUpdatedForRestaurantWide(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("deviceId") String deviceId);

    @Query("SELECT b FROM Bill b WHERE b.restaurantId = :restaurantId " +
           "AND b.serverUpdatedAt > :lastSyncTimestamp")
    org.springframework.data.domain.Page<Bill> findUpdatedForRestaurantWide(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("deviceId") String deviceId,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT b FROM Bill b
            WHERE b.restaurantId = :restaurantId
              AND b.serverUpdatedAt > :lastSyncTimestamp
              AND (
                    b.createdTerminalId = :terminalId
                    OR b.currentOwnerTerminalId = :terminalId
                    OR LOWER(b.orderStatus) IN ('completed','paid','cancelled')
                    OR (b.refundAmount IS NOT NULL AND b.refundAmount > 0)
                    OR (b.sourceChannel IS NOT NULL AND LOWER(b.sourceChannel) IN ('zomato','swiggy','own_website'))
                  )
            """)
    org.springframework.data.domain.Page<Bill> findUpdatedForTerminal(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("terminalId") String terminalId,
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

    // --- Easebuzz / metrics queries (v2 port) ---
    Optional<Bill> findByGatewayTxnId(String gatewayTxnId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT b.restaurant_id, COUNT(b.id) AS order_count,
                   COALESCE(SUM(b.commission_amount), 0) AS total_commission,
                   COALESCE(SUM(b.total_amount), 0) AS total_revenue
            FROM bills b
            WHERE b.commission_amount IS NOT NULL AND b.is_deleted = false
            GROUP BY b.restaurant_id ORDER BY b.restaurant_id
            """, nativeQuery = true)
    java.util.List<Object[]> findCommissionSummary();

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT b.restaurant_id, COALESCE(SUM(b.settled_amount), 0) AS total_settled,
                   COALESCE(SUM(b.commission_amount), 0) AS total_commission,
                   COUNT(b.id) AS order_count, MAX(b.settled_at) AS last_settled_at
            FROM bills b
            WHERE b.settled_amount IS NOT NULL
            GROUP BY b.restaurant_id ORDER BY b.restaurant_id
            """, nativeQuery = true)
    java.util.List<Object[]> findSettlementSummary();

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.paymentMode = :mode AND LOWER(b.paymentStatus) IN ('success','paid')")
    long countSuccessfulByMode(@org.springframework.data.repository.query.Param("mode") String mode);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.paymentMode = :mode")
    long countByMode(@org.springframework.data.repository.query.Param("mode") String mode);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.isDeleted = false AND b.createdAt >= :since AND LOWER(b.paymentStatus) IN ('success','paid')")
    java.math.BigDecimal sumRevenueSince(@org.springframework.data.repository.query.Param("since") long since);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.createdAt >= :since")
    long countSince(@org.springframework.data.repository.query.Param("since") long since);

    @org.springframework.data.jpa.repository.Query("SELECT b.paymentMode, b.paymentStatus, COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.createdAt BETWEEN :from AND :to GROUP BY b.paymentMode, b.paymentStatus")
    java.util.List<Object[]> countByModeAndStatusBetween(@org.springframework.data.repository.query.Param("from") long from, @org.springframework.data.repository.query.Param("to") long to);

    @org.springframework.data.jpa.repository.Query("SELECT b.paymentMode, COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.createdAt BETWEEN :from AND :to AND LOWER(b.paymentStatus) IN ('success','paid') GROUP BY b.paymentMode")
    java.util.List<Object[]> countSuccessfulByModeBetween(@org.springframework.data.repository.query.Param("from") long from, @org.springframework.data.repository.query.Param("to") long to);

    // ─── v2 port: settlement / metrics finders not present in the v1 repository ───
    java.util.List<Bill> findBySettledAmountIsNotNull();

    java.util.List<Bill> findByRestaurantIdAndSettledAmountIsNotNull(Long restaurantId);

    Optional<Bill> findByRestaurantIdAndLifetimeOrderIdAndIsDeletedFalse(Long restaurantId, Long lifetimeOrderId);

    @org.springframework.data.jpa.repository.Query("SELECT b.paymentMode, COUNT(b) FROM Bill b WHERE b.isDeleted = false GROUP BY b.paymentMode")
    java.util.List<Object[]> countByPaymentMode();

    // Bills updated since lastSync, excluding own-device bills UNLESS they are deleted.
    @org.springframework.data.jpa.repository.Query("SELECT b FROM Bill b WHERE b.restaurantId = :restaurantId "
            + "AND b.serverUpdatedAt > :lastSyncTimestamp "
            + "AND (b.deviceId != :deviceId OR b.isDeleted = true)")
    java.util.List<Bill> findUpdatedExcludingOwnActiveOnly(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("deviceId") String deviceId);
}
