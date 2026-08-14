package com.khanabook.saas.repository;

import com.khanabook.saas.entity.FeatureFlagOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureFlagOverrideRepository extends JpaRepository<FeatureFlagOverride, Long> {

    Optional<FeatureFlagOverride> findByFlagKeyAndRestaurantId(String flagKey, Long restaurantId);

    List<FeatureFlagOverride> findByRestaurantId(Long restaurantId);

    void deleteByFlagKeyAndRestaurantId(String flagKey, Long restaurantId);
}
