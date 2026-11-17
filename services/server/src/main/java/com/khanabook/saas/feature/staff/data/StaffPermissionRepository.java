package com.khanabook.saas.feature.staff.data;

import java.util.Optional;

/**
 * @deprecated Retired in V124. Staff permissions are now constant and role-bound.
 */
@Deprecated
public interface StaffPermissionRepository {
    default Optional<StaffPermission> findByRestaurantIdAndUserIdAndPermissionKey(Long restaurantId, Long userId, String permissionKey) {
        return Optional.empty();
    }
}
