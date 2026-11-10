package com.khanabook.saas.feature.subscription.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantSubscriptionRepository extends JpaRepository<RestaurantSubscription, Long> {

    Optional<RestaurantSubscription> findByRestaurantId(Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM RestaurantSubscription s WHERE s.restaurantId = :restaurantId")
    Optional<RestaurantSubscription> findByRestaurantIdWithLock(@Param("restaurantId") Long restaurantId);

    List<RestaurantSubscription> findAllByStatusInAndCurrentPeriodEndBefore(
            List<SubscriptionStatus> statuses, Long timestamp);
}
