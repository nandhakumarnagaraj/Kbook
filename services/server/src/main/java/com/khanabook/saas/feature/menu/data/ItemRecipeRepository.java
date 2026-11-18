package com.khanabook.saas.feature.menu.data;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @deprecated Retired in V127. POS operates on finished MenuItem and ItemVariant catalog records.
 */
@Deprecated
@Repository
public class ItemRecipeRepository {

    public List<ItemRecipe> findByRestaurantIdAndMenuItemIdAndIsDeletedFalse(Long restaurantId, Long menuItemId) {
        return Collections.emptyList();
    }

    public List<ItemRecipe> findByRestaurantIdAndIsDeletedFalse(Long restaurantId) {
        return Collections.emptyList();
    }

    public List<Long> findMenuItemIdsWithRecipes(Long restaurantId) {
        return Collections.emptyList();
    }

    public List<ItemRecipe> findByRestaurantIdAndRawMaterial(Long restaurantId, Long rawMaterialId) {
        return Collections.emptyList();
    }

    public Optional<ItemRecipe> findById(Long id) {
        return Optional.empty();
    }

    public <S extends ItemRecipe> S save(S entity) {
        return entity;
    }

    public <S extends ItemRecipe> S saveAndFlush(S entity) {
        return save(entity);
    }

    public void flush() {}

    public void deleteById(Long id) {}

    public void deleteAll() {}
}
