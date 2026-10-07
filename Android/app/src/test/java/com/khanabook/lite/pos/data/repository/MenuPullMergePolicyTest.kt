package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.MenuPullMergePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A pulled record is server-authoritative, with one exception: a row that still holds unpushed
 * edits. The pulled entity is built with isSynced = true and no changed_fields mask, so writing
 * it over a dirty row reverted the edit AND marked the row acknowledged, removing it from
 * getUnsyncedMenuItems - the edit ended up neither on the device nor on the server.
 *
 * These lock the exception down for all three record types that flow through the pull.
 */
class MenuPullMergePolicyTest {

    private fun pulledItem(deleted: Boolean = false) = MenuItemEntity(
        id = 9L,
        categoryId = 10L,
        name = "Pulled",
        basePrice = "100",
        isSynced = true,
        isDeleted = deleted
    )

    private fun localItem(id: Long = 1L, synced: Boolean) = MenuItemEntity(
        id = id,
        categoryId = 10L,
        name = "Edited",
        basePrice = "250",
        isSynced = synced
    )

    @Test
    fun `a menu item with unpushed edits is left untouched`() {
        assertNull(MenuPullMergePolicy.resolveMenuItem(pulledItem(), localItem(synced = false)))
    }

    @Test
    fun `an acknowledged menu item takes the server version under its local id`() {
        val resolved = MenuPullMergePolicy.resolveMenuItem(pulledItem(), localItem(id = 3L, synced = true))

        assertEquals(3L, resolved!!.id)
        assertEquals("Pulled", resolved.name)
        assertEquals(true, resolved.isSynced)
    }

    @Test
    fun `a server-side delete still lands on an acknowledged menu item`() {
        val resolved = MenuPullMergePolicy.resolveMenuItem(pulledItem(deleted = true), localItem(synced = true))

        assertEquals(true, resolved!!.isDeleted)
    }

    @Test
    fun `a variant with unpushed edits is left untouched`() {
        assertNull(
            MenuPullMergePolicy.resolveVariant(
                ItemVariantEntity(id = 9L, menuItemId = 1L, variantName = "Small", price = "150", isSynced = true),
                ItemVariantEntity(id = 2L, menuItemId = 1L, variantName = "Small", price = "199", isSynced = false)
            )
        )
    }

    @Test
    fun `an acknowledged variant takes the server version`() {
        val resolved = MenuPullMergePolicy.resolveVariant(
            ItemVariantEntity(id = 9L, menuItemId = 1L, variantName = "Small", price = "150", isSynced = true),
            ItemVariantEntity(id = 2L, menuItemId = 1L, variantName = "Small", price = "199", isSynced = true)
        )

        assertEquals(2L, resolved!!.id)
        assertEquals("150", resolved.price)
    }

    @Test
    fun `a category with unpushed edits is left untouched`() {
        assertNull(
            MenuPullMergePolicy.resolveCategory(
                CategoryEntity(id = 9L, name = "Starters", isVeg = true, isSynced = true),
                CategoryEntity(id = 4L, name = "Starters", isVeg = true, isSynced = false)
            )
        )
    }

    @Test
    fun `an acknowledged category takes the server version and its delete`() {
        val resolved = MenuPullMergePolicy.resolveCategory(
            CategoryEntity(id = 9L, name = "Starters", isVeg = true, isSynced = true, isDeleted = true),
            CategoryEntity(id = 4L, name = "Starters", isVeg = true, isSynced = true)
        )

        assertEquals(4L, resolved!!.id)
        assertEquals(true, resolved.isDeleted)
    }
}
