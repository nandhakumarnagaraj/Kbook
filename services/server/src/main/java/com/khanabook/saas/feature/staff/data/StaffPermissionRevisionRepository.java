package com.khanabook.saas.feature.staff.data;

import java.util.Optional;

/**
 * @deprecated Retired in V124. Staff permission revisions are now constant and role-bound.
 */
@Deprecated
public interface StaffPermissionRevisionRepository {
    default Optional<StaffPermissionRevision> findByRestaurantIdAndUserId(Long restaurantId, Long userId) {
        return Optional.empty();
    }
}
