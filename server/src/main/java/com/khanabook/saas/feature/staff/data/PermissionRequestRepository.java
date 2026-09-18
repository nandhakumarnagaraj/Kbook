package com.khanabook.saas.feature.staff.data;

import com.khanabook.saas.feature.staff.data.PermissionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionRequestRepository extends JpaRepository<PermissionRequest, Long> {

    List<PermissionRequest> findByRestaurantIdAndStatus(Long restaurantId, String status);

    List<PermissionRequest> findByRestaurantIdAndUserId(Long restaurantId, Long userId);

    List<PermissionRequest> findByRestaurantIdAndUserIdAndPermissionKeyAndStatus(
            Long restaurantId, Long userId, String permissionKey, String status);
}
