package com.khanabook.saas.feature.inventory.data;

import com.khanabook.saas.feature.menu.data.ItemRecipe;

import java.math.BigDecimal;

/**
 * Wire shapes for the recipe endpoints.
 *
 * <p>These endpoints used to return the {@link ItemRecipe} entity directly. With
 * {@code spring.jpa.open-in-view=false} the {@code rawMaterial} association arrives as an
 * uninitialized proxy, which JacksonConfig serializes as {@code {"id":5}} — so the owner UI
 * could not name or unit the ingredient it was configuring. The name and unit travel as
 * values instead.
 */
public class RecipeDtos {

    public record RecipeLineResponse(
            Long id,
            Long menuItemId,
            Long rawMaterialId,
            String materialName,
            String unit,
            BigDecimal quantityPerItem
    ) {
        /** Must be called with the material loaded (inside the transaction that read the recipe). */
        public static RecipeLineResponse of(ItemRecipe recipe) {
            RawMaterial material = recipe.getRawMaterial();
            return new RecipeLineResponse(
                    recipe.getId(),
                    recipe.getMenuItemId(),
                    material.getId(),
                    material.getName(),
                    material.getUnit(),
                    recipe.getQuantityPerItem());
        }
    }
}
