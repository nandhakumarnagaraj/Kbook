package com.khanabook.lite.pos.feature.menu.data


import androidx.room.Embedded
import androidx.room.Relation
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity

data class MenuWithVariants(
    @Embedded val menuItem: MenuItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "menu_item_id"
    )
    val variants: List<ItemVariantEntity>
)


