package com.khanabook.saas.repository;

import com.khanabook.saas.entity.ItemRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRecipeRepository extends JpaRepository<ItemRecipe, Long> {

    List<ItemRecipe> findByRestaurantIdAndMenuItemIdAndIsDeletedFalse(Long restaurantId, Long menuItemId);

    List<ItemRecipe> findByRestaurantIdAndIsDeletedFalse(Long restaurantId);

    @Query("SELECT DISTINCT r.menuItemId FROM ItemRecipe r " +
           "WHERE r.restaurantId = :restaurantId AND r.isDeleted = false")
    List<Long> findMenuItemIdsWithRecipes(@Param("restaurantId") Long restaurantId);

    @Query("SELECT r FROM ItemRecipe r WHERE r.restaurantId = :restaurantId " +
           "AND r.rawMaterial.id = :rawMaterialId AND r.isDeleted = false")
    List<ItemRecipe> findByRestaurantIdAndRawMaterial(@Param("restaurantId") Long restaurantId,
                                                      @Param("rawMaterialId") Long rawMaterialId);
}
