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
 * However, a dirty row is only protected while it holds an edit at least as new as the server's
 * version. When the pulled (server) record is STRICTLY NEWER — the "Incoming record is older
 * than the server record" case — keeping the stale local copy re-pushes it forever (the loop
 * seen on terminal 66: localIds 108, 551, 552, 553 rejected every cycle). LWW says the newer
 * server row wins, so the pull adopts it and the row drops out of the unsynced set.
 *
 * These lock the timestamp-aware rule down for all three record types that flow through the pull.
 */
class MenuPullMergePolicyTest {

    private fun pulledItem(deleted: Boolean = false, updatedAt: Long = 200L) = MenuItemEntity(
        id = 9L,
        categoryId = 10L,
        name = "Pulled",
        basePrice = "100",
        isSynced = true,
        isDeleted = deleted,
        updatedAt = updatedAt
    )

    private fun localItem(id: Long = 1L, synced: Boolean, updatedAt: Long = 100L) = MenuItemEntity(
        id = id,
        categoryId = 10L,
        name = "Edited",
        basePrice = "250",
        isSynced = synced,
        updatedAt = updatedAt
    )

    @Test
    fun `a menu item with a newer unpushed edit is left untouched`() {
        assertNull(MenuPullMergePolicy.resolveMenuItem(pulledItem(updatedAt = 100L), localItem(synced = false, updatedAt = 200L)))
    }

    @Test
    fun `a stale unsynced menu item adopts the newer server version`() {
        val resolved = MenuPullMergePolicy.resolveMenuItem(pulledItem(updatedAt = 200L), localItem(id = 3L, synced = false, updatedAt = 100L))

        assertEquals(3L, resolved!!.id)
        assertEquals("Pulled", resolved.name)
        assertEquals(true, resolved.isSynced)
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
    fun `a server-side delete lands on a stale unsynced menu item too`() {
        val resolved = MenuPullMergePolicy.resolveMenuItem(
            pulledItem(deleted = true, updatedAt = 200L),
            localItem(synced = false, updatedAt = 100L)
        )

        assertEquals(true, resolved!!.isDeleted)
    }

    @Test
    fun `a variant with a newer unpushed edit is left untouched`() {
        assertNull(
            MenuPullMergePolicy.resolveVariant(
                ItemVariantEntity(id = 9L, menuItemId = 1L, variantName = "Small", price = "150", isSynced = true, updatedAt = 100L),
                ItemVariantEntity(id = 2L, menuItemId = 1L, variantName = "Small", price = "199", isSynced = false, updatedAt = 200L)
            )
        )
    }

    @Test
    fun `a stale unsynced variant adopts the newer server version`() {
        val resolved = MenuPullMergePolicy.resolveVariant(
            ItemVariantEntity(id = 9L, menuItemId = 1L, variantName = "Small", price = "150", isSynced = true, updatedAt = 200L),
            ItemVariantEntity(id = 2L, menuItemId = 1L, variantName = "Small", price = "199", isSynced = false, updatedAt = 100L)
        )

        assertEquals(2L, resolved!!.id)
        assertEquals("150", resolved.price)
        assertEquals(true, resolved.isSynced)
    }

    @Test
    fun `an acknowledged variant takes the server version`() {
        val resolved = MenuPullMergePolicy.resolveVariant(
            ItemVariantEntity(id = 9L, menuItemId = 1L, variantName = "Small", price = "150", isSynced = true, updatedAt = 200L),
            ItemVariantEntity(id = 2L, menuItemId = 1L, variantName = "Small", price = "199", isSynced = true, updatedAt = 100L)
        )

        assertEquals(2L, resolved!!.id)
        assertEquals("150", resolved.price)
    }

    @Test
    fun `a category with a newer unpushed edit is left untouched`() {
        assertNull(
            MenuPullMergePolicy.resolveCategory(
                CategoryEntity(id = 9L, name = "Starters", isVeg = true, isSynced = true, updatedAt = 100L),
                CategoryEntity(id = 4L, name = "Starters", isVeg = true, isSynced = false, updatedAt = 200L)
            )
        )
    }

    @Test
    fun `a stale unsynced category adopts the newer server version`() {
        val resolved = MenuPullMergePolicy.resolveCategory(
            CategoryEntity(id = 9L, name = "Starters", isVeg = true, isSynced = true, updatedAt = 200L),
            CategoryEntity(id = 4L, name = "Starters", isVeg = true, isSynced = false, updatedAt = 100L)
        )

        assertEquals(4L, resolved!!.id)
        assertEquals(true, resolved.isSynced)
    }

    @Test
    fun `an acknowledged category takes the server version and its delete`() {
        val resolved = MenuPullMergePolicy.resolveCategory(
            CategoryEntity(id = 9L, name = "Starters", isVeg = true, isSynced = true, isDeleted = true, updatedAt = 200L),
            CategoryEntity(id = 4L, name = "Starters", isVeg = true, isSynced = true, updatedAt = 100L)
        )

        assertEquals(4L, resolved!!.id)
        assertEquals(true, resolved.isDeleted)
    }
}