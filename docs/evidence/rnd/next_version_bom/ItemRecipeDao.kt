package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.ItemRecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemRecipeDao {

    @Query("SELECT * FROM item_recipes WHERE restaurant_id = :restaurantId AND menu_item_id = :menuItemId AND is_deleted = 0")
    suspend fun getRecipesForMenuItem(restaurantId: Long, menuItemId: Long): List<ItemRecipeEntity>

    @Query("SELECT * FROM item_recipes WHERE restaurant_id = :restaurantId AND menu_item_id = :menuItemId AND is_deleted = 0")
    fun getRecipesForMenuItemFlow(restaurantId: Long, menuItemId: Long): Flow<List<ItemRecipeEntity>>

    @Query("SELECT * FROM item_recipes WHERE restaurant_id = :restaurantId AND is_deleted = 0")
    suspend fun getAll(restaurantId: Long): List<ItemRecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(recipe: ItemRecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(recipes: List<ItemRecipeEntity>)

    @Query("DELETE FROM item_recipes WHERE menu_item_id = :menuItemId AND restaurant_id = :restaurantId")
    suspend fun deleteForMenuItem(menuItemId: Long, restaurantId: Long)

    @Query("DELETE FROM item_recipes WHERE restaurant_id = :restaurantId")
    suspend fun deleteAllForRestaurant(restaurantId: Long)
}
