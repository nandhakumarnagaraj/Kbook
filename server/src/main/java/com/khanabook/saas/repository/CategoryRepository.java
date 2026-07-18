package com.khanabook.saas.repository;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.sync.repository.SyncRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CategoryRepository extends SyncRepository<Category, Long> {

	Optional<Category> findByIdAndRestaurantIdAndIsDeletedFalse(Long id, Long restaurantId);

	List<Category> findByRestaurantIdAndIsDeletedFalseAndIsActiveTrueOrderByNameAsc(Long restaurantId);
}
