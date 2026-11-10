package com.khanabook.saas.feature.subscription.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {
    List<SubscriptionPlan> findAllByIsActiveTrueOrderBySortOrderAsc();
    Optional<SubscriptionPlan> findByCode(String code);
}
