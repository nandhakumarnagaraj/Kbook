package com.khanabook.saas.feature.staff.data;

import java.util.List;

/**
 * @deprecated Retired in V124. Role templates are retired in favor of OWNER / SHOP_STAFF / KBOOK_ADMIN.
 */
@Deprecated
public interface RoleTemplateRepository {
    default List<RoleTemplate> findByRestaurantId(Long restaurantId) {
        return List.of();
    }
}
