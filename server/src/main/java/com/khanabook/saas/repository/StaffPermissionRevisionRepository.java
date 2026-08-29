package com.khanabook.saas.repository;

import com.khanabook.saas.entity.StaffPermissionRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffPermissionRevisionRepository
        extends JpaRepository<StaffPermissionRevision, StaffPermissionRevision.PK> {

    Optional<StaffPermissionRevision> findByRestaurantIdAndUserId(Long restaurantId, Long userId);
}
