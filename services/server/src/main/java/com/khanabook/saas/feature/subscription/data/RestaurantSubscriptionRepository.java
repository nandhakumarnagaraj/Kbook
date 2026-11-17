package com.khanabook.saas.feature.subscription.data;

import java.util.List;
import java.util.Optional;

/**
 * @deprecated Retired in V124. Universal Free Lifetime access is now standard.
 */
@Deprecated
public interface RestaurantSubscriptionRepository {
    default Optional<RestaurantSubscription> findByRestaurantId(Long restaurantId) {
        return Optional.empty();
    }
    default Optional<RestaurantSubscription> findByRestaurantIdWithLock(Long restaurantId) {
        return Optional.empty();
    }
    default List<RestaurantSubscription> findAllByStatusInAndCurrentPeriodEndBefore(Object statuses, long time) {
        return List.of();
    }
    default <S extends RestaurantSubscription> S save(S entity) {
        return entity;
    }
}
