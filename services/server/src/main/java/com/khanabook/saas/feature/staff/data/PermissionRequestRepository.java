package com.khanabook.saas.feature.staff.data;

import java.util.List;
import java.util.Optional;

/**
 * @deprecated Retired in V124. Custom permission requests are no longer stored in DB.
 */
@Deprecated
public interface PermissionRequestRepository {
    default List<PermissionRequest> findByRestaurantIdAndStatus(Long restaurantId, String status) {
        return List.of();
    }
}
