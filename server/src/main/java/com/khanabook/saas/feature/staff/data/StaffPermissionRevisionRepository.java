package com.khanabook.saas.feature.staff.data;

import com.khanabook.saas.feature.staff.data.StaffPermissionRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffPermissionRevisionRepository
        extends JpaRepository<StaffPermissionRevision, StaffPermissionRevision.PK> {

    Optional<StaffPermissionRevision> findByRestaurantIdAndUserId(Long restaurantId, Long userId);
}
