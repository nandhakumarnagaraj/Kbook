package com.khanabook.saas.feature.inventory.data;

import com.khanabook.saas.feature.inventory.data.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    List<Vendor> findByRestaurantIdAndIsDeletedFalseOrderByNameAsc(Long restaurantId);
}
