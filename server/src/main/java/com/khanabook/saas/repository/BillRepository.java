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

    List<Bill> findBySettledAmountIsNotNull();

    List<Bill> findByRestaurantIdAndSettledAmountIsNotNull(Long restaurantId);

    @Query(value = """
            SELECT b.restaurant_id, COALESCE(SUM(b.settled_amount), 0) AS total_settled,
                   COALESCE(SUM(b.commission_amount), 0) AS total_commission,
                   COUNT(b.id) AS order_count, MAX(b.settled_at) AS last_settled_at
            FROM bills b
            WHERE b.settled_amount IS NOT NULL
            GROUP BY b.restaurant_id
            ORDER BY b.restaurant_id
            """, nativeQuery = true)
    List<Object[]> findSettlementSummary();

    // Returns bills updated since lastSync, excluding own-device bills UNLESS they are deleted.
    @Query("SELECT b FROM Bill b WHERE b.restaurantId = :restaurantId " +
           "AND b.serverUpdatedAt > :lastSyncTimestamp " +
           "AND (b.deviceId != :deviceId OR b.isDeleted = true)")
    List<Bill> findUpdatedExcludingOwnActiveOnly(
            @Param("restaurantId") Long restaurantId,
            @Param("lastSyncTimestamp") Long lastSyncTimestamp,
            @Param("deviceId") String deviceId);

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

    Optional<Bill> findByGatewayTxnId(String gatewayTxnId);

    Optional<Bill> findByRestaurantIdAndLifetimeOrderIdAndIsDeletedFalse(Long restaurantId, Long lifetimeOrderId);

    Optional<Bill> findByRestaurantIdAndDeviceIdAndLocalIdAndIsDeletedFalse(
            Long restaurantId, String deviceId, Long localId);

    @Query(value = """
            SELECT b.restaurant_id, COUNT(b.id) AS order_count,
                   COALESCE(SUM(b.commission_amount), 0) AS total_commission,
                   COALESCE(SUM(b.total_amount), 0) AS total_revenue
            FROM bills b
            WHERE b.commission_amount IS NOT NULL AND b.is_deleted = false
            GROUP BY b.restaurant_id
            ORDER BY b.restaurant_id
            """, nativeQuery = true)
    List<Object[]> findCommissionSummary();

    @Query("SELECT b.paymentMode, COUNT(b) FROM Bill b WHERE b.isDeleted = false GROUP BY b.paymentMode")
    List<Object[]> countByPaymentMode();

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.paymentMode = :mode AND LOWER(b.paymentStatus) IN ('success','paid')")
    long countSuccessfulByMode(@Param("mode") String mode);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.paymentMode = :mode")
    long countByMode(@Param("mode") String mode);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.isDeleted = false AND b.createdAt >= :since AND LOWER(b.paymentStatus) IN ('success','paid')")
    BigDecimal sumRevenueSince(@Param("since") long since);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.isDeleted = false AND b.createdAt >= :since")
    long countSince(@Param("since") long since);
}
