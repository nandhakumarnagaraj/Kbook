package com.khanabook.saas.storefront.repository;

import com.khanabook.saas.storefront.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    Optional<CustomerOrder> findByTrackingToken(String trackingToken);
    Optional<CustomerOrder> findByRestaurantIdAndId(Long restaurantId, Long id);
    List<CustomerOrder> findByRestaurantIdOrderByCreatedAtDescIdDesc(Long restaurantId);
    boolean existsByPublicOrderCode(String publicOrderCode);

    @Query("SELECT SUM(o.totalAmount) FROM CustomerOrder o WHERE o.orderStatus = 'COMPLETED' OR o.paymentStatus IN ('SUCCESS', 'PAID')")
    BigDecimal sumCompletedRevenue();

    @Query("SELECT o.restaurantId, COUNT(o) FROM CustomerOrder o GROUP BY o.restaurantId")
    List<Object[]> countGroupedByRestaurant();

    @Query("SELECT SUM(o.totalAmount) FROM CustomerOrder o WHERE o.restaurantId = :restaurantId AND (o.orderStatus = 'COMPLETED' OR o.paymentStatus IN ('SUCCESS', 'PAID'))")
    BigDecimal sumCompletedRevenueByRestaurant(@Param("restaurantId") Long restaurantId);

    long countByRestaurantId(Long restaurantId);
}
