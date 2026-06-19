package com.khanabook.saas.repository;

import com.khanabook.saas.entity.FssaiTracker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FssaiTrackerRepository extends JpaRepository<FssaiTracker, Long> {
    Optional<FssaiTracker> findByRestaurantId(Long restaurantId);
}
