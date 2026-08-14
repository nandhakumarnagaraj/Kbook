package com.khanabook.saas.repository;

import com.khanabook.saas.entity.StaffPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffPermissionRepository extends JpaRepository<StaffPermission, Long> {

    List<StaffPermission> findByRestaurantIdAndUserId(Long restaurantId, Long userId);

    List<StaffPermission> findByRestaurantIdAndUserIdAndGrantedTrue(Long restaurantId, Long userId);

    Optional<StaffPermission> findByRestaurantIdAndUserIdAndPermissionKey(Long restaurantId, Long userId, String permissionKey);

    List<StaffPermission> findByRestaurantId(Long restaurantId);

    void deleteByRestaurantIdAndUserId(Long restaurantId, Long userId);
}
