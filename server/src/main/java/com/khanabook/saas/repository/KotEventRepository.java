package com.khanabook.saas.repository;

import com.khanabook.saas.entity.KotEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KotEventRepository extends JpaRepository<KotEvent, Long> {

	Optional<KotEvent> findByRestaurantIdAndPublicTokenAndKotRevision(
			Long restaurantId, String publicToken, String kotRevision);

	Page<KotEvent> findByRestaurantIdAndCreatedAtGreaterThan(
			Long restaurantId, Long lastSyncTimestamp, Pageable pageable);

	Page<KotEvent> findByRestaurantIdAndCreatedAtGreaterThanAndDeviceIdNot(
			Long restaurantId, Long lastSyncTimestamp, String deviceId, Pageable pageable);
}