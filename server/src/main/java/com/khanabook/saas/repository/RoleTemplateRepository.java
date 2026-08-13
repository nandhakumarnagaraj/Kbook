package com.khanabook.saas.repository;

import com.khanabook.saas.entity.RoleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleTemplateRepository extends JpaRepository<RoleTemplate, Long> {

    List<RoleTemplate> findByRestaurantId(Long restaurantId);

    Optional<RoleTemplate> findByRestaurantIdAndName(Long restaurantId, String name);

    List<RoleTemplate> findByRestaurantIdAndIsDefaultTrue(Long restaurantId);
}
