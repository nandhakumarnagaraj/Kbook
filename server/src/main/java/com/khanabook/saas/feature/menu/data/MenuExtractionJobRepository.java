package com.khanabook.saas.feature.menu.data;

import com.khanabook.saas.feature.menu.data.MenuExtractionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuExtractionJobRepository extends JpaRepository<MenuExtractionJob, Long> {
    List<MenuExtractionJob> findByRestaurantId(Long restaurantId);

    Optional<MenuExtractionJob> findByIdAndRestaurantId(Long id, Long restaurantId);
    List<MenuExtractionJob> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

}
