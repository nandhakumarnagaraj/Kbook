package com.khanabook.saas.feature.compliance.data;

import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FssaiTrackerRepository extends JpaRepository<FssaiTracker, Long> {
    Optional<FssaiTracker> findByRestaurantId(Long restaurantId);
}
