package com.khanabook.saas.repository;

import com.khanabook.saas.entity.DeviceRegistrationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRegistrationRequestRepository extends JpaRepository<DeviceRegistrationRequest, Long> {

    List<DeviceRegistrationRequest> findByRestaurantIdAndStatusOrderByRequestedAtDesc(Long restaurantId, String status);

    List<DeviceRegistrationRequest> findByRestaurantIdOrderByRequestedAtDesc(Long restaurantId);

    Optional<DeviceRegistrationRequest> findByRestaurantIdAndDeviceIdAndStatus(Long restaurantId, String deviceId, String status);

    @Query("SELECT r FROM DeviceRegistrationRequest r WHERE r.restaurantId = :restaurantId AND r.deviceId = :deviceId ORDER BY r.requestedAt DESC LIMIT 1")
    Optional<DeviceRegistrationRequest> findMostRecentByRestaurantIdAndDeviceId(@Param("restaurantId") Long restaurantId, @Param("deviceId") String deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DeviceRegistrationRequest r WHERE r.id = :id")
    Optional<DeviceRegistrationRequest> findByIdWithLock(@Param("id") Long id);
}
