package com.khanabook.saas.repository;

import com.khanabook.saas.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByRestaurantIdAndActiveTrue(Long restaurantId);

    Optional<DeviceToken> findByToken(String token);

    Optional<DeviceToken> findByRestaurantIdAndDeviceId(Long restaurantId, String deviceId);

    long countByRestaurantIdAndActiveTrue(Long restaurantId);
}
