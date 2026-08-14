package com.khanabook.saas.repository;

import com.khanabook.saas.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    Optional<CustomerProfile> findByRestaurantIdAndPhoneHash(Long restaurantId, String phoneHash);

    List<CustomerProfile> findByRestaurantIdOrderByTotalSpendDesc(Long restaurantId);

    @Query("SELECT c.segment, COUNT(c) FROM CustomerProfile c WHERE c.restaurantId = :restaurantId GROUP BY c.segment")
    List<Object[]> countBySegmentForRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(c) FROM CustomerProfile c WHERE c.restaurantId = :restaurantId AND c.totalOrders >= :minOrders")
    long countRepeatCustomers(@Param("restaurantId") Long restaurantId, @Param("minOrders") long minOrders);

    @Query("SELECT COALESCE(AVG(c.averageOrderValue), 0) FROM CustomerProfile c WHERE c.restaurantId = :restaurantId")
    BigDecimal avgOrderValueForRestaurant(@Param("restaurantId") Long restaurantId);
}