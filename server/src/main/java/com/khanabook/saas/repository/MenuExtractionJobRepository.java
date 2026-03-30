package com.khanabook.saas.repository;

import com.khanabook.saas.entity.MenuExtractionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuExtractionJobRepository extends JpaRepository<MenuExtractionJob, Long> {
    List<MenuExtractionJob> findByRestaurantId(Long restaurantId);
}
