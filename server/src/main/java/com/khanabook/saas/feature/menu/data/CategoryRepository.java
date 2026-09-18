package com.khanabook.saas.feature.menu.data;

import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.sync.data.SyncRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CategoryRepository extends SyncRepository<Category, Long> {

	Optional<Category> findByIdAndRestaurantIdAndIsDeletedFalse(Long id, Long restaurantId);

	List<Category> findByRestaurantIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(Long restaurantId);
}
