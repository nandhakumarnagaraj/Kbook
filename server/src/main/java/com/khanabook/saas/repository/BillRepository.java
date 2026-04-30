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

    Optional<Bill> findByRestaurantIdAndLifetimeOrderIdAndIsDeletedFalse(Long restaurantId, Long lifetimeOrderId);

    Optional<Bill> findByRestaurantIdAndDeviceIdAndLocalIdAndIsDeletedFalse(
            Long restaurantId, String deviceId, Long localId);
}
