package com.khanabook.saas.sync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface SyncRepository<T, ID> extends JpaRepository<T, ID> {

	List<T> findByRestaurantIdAndUpdatedAtGreaterThanAndDeviceIdNot(Long restaurantId, Long lastSyncTimestamp,
			String deviceId);

	List<T> findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(Long restaurantId, Long lastSyncTimestamp,
			String deviceId);

	List<T> findByRestaurantIdAndServerUpdatedAtGreaterThan(Long restaurantId, Long lastSyncTimestamp);

	Optional<T> findByRestaurantIdAndDeviceIdAndLocalId(Long restaurantId, String deviceId, Long localId);

	List<T> findByRestaurantIdAndDeviceIdAndLocalIdIn(Long restaurantId, String deviceId, List<Long> localIds);

	List<T> findByRestaurantIdAndLocalIdIn(Long restaurantId, List<Long> localIds);
}
