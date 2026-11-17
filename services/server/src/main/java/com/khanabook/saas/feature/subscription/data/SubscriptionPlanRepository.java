package com.khanabook.saas.feature.subscription.data;

import java.util.List;
import java.util.Optional;

/**
 * @deprecated Retired in V124. Universal Free Lifetime access is now standard.
 */
@Deprecated
public interface SubscriptionPlanRepository {
    default Optional<SubscriptionPlan> findByCode(String code) {
        return Optional.empty();
    }
    default List<SubscriptionPlan> findAll() {
        return List.of();
    }
    default <S extends SubscriptionPlan> S save(S entity) {
        return entity;
    }
}
