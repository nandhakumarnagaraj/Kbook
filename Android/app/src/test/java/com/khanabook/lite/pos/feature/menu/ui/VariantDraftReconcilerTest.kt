package com.khanabook.lite.pos.feature.menu.ui

import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RC2: editing an existing variant used to soft-delete every variant of the item and
 * re-add the edited list under fresh ids. A rename therefore produced two rows - the old one
 * on the server (the delete frequently never landed) and the new one on the device - and
 * the reconciler is the code that replaces that flow.
 */
class VariantDraftReconcilerTest {

    private fun variant(id: Long, name: String, price: String) = ItemVariantEntity(
        id = id,
        menuItemId = 1L,
        variantName = name,
        price = price,
        isSynced = true,
        updatedAt = 100L
    )

    private val stored = listOf(
        variant(11L, "Half", "120.00"),
        variant(12L, "Full", "200.00")
    )

    @Test
    fun `renaming a variant updates it in place instead of delete plus add`() {
        val ops = VariantDraftReconciler.plan(
            stored = stored,
            drafts = listOf(
                EditableVariantDraft(id = 11L, name = "Small", price = 120.0),
                EditableVariantDraft(id = 12L, name = "Full", price = 200.0)
            ),
            now = 500L
        )

        assertEquals(1, ops.size)
        val update = ops.single() as VariantDraftOp.Update
        assertEquals(11L, update.variant.id)
        assertEquals("Small", update.variant.variantName)
        assertEquals("120.0", update.variant.price)
        assertEquals(false, update.variant.isSynced)
        assertEquals(500L, update.variant.updatedAt)
    }

    @Test
    fun `untouched variants are not written at all`() {
        val ops = VariantDraftReconciler.plan(
            stored = stored,
            drafts = listOf(
                EditableVariantDraft(id = 11L, name = "Half", price = 120.0),
                EditableVariantDraft(id = 12L, name = "Full", price = 200.0)
            )
        )

        assertTrue(ops.isEmpty())
    }

    @Test
    fun `a price change on an existing variant is an update, not a re-add`() {
        val ops = VariantDraftReconciler.plan(
            stored = stored,
            drafts = listOf(
                EditableVariantDraft(id = 11L, name = "Half", price = 150.0),
                EditableVariantDraft(id = 12L, name = "Full", price = 200.0)
            ),
            now = 501L
        )

        assertEquals(1, ops.size)
        val update = ops.single() as VariantDraftOp.Update
        assertEquals(11L, update.variant.id)
        assertEquals("150.0", update.variant.price)
    }

    @Test
    fun `removing a variant from the dialog deletes exactly that row`() {
        val ops = VariantDraftReconciler.plan(
            stored = stored,
            drafts = listOf(EditableVariantDraft(id = 12L, name = "Full", price = 200.0))
        )

        assertEquals(1, ops.size)
        val delete = ops.single() as VariantDraftOp.Delete
        assertEquals(11L, delete.variant.id)
    }

    @Test
    fun `a variant added in the dialog has no id and is inserted`() {
        val ops = VariantDraftReconciler.plan(
            stored = stored,
            drafts = stored.map { EditableVariantDraft(id = it.id, name = it.variantName, price = it.price.toDouble()) } +
                EditableVariantDraft(id = null, name = "Jumbo", price = 350.0)
        )

        assertEquals(1, ops.size)
        val add = ops.single() as VariantDraftOp.Add
        assertEquals("Jumbo", add.name)
        assertEquals(350.0, add.price, 0.0)
    }

    @Test
    fun `mixed edits emit one op per touched variant`() {
        val ops = VariantDraftReconciler.plan(
            stored = stored,
            drafts = listOf(
                EditableVariantDraft(id = 11L, name = "Small", price = 120.0),
                EditableVariantDraft(id = null, name = "Family", price = 400.0)
            ),
            now = 502L
        )

        assertEquals(3, ops.size)
        assertTrue(ops.any { it is VariantDraftOp.Delete && it.variant.id == 12L })
        assertTrue(ops.any { it is VariantDraftOp.Update && it.variant.id == 11L })
        assertTrue(ops.any { it is VariantDraftOp.Add && it.name == "Family" })
    }

    @Test
    fun `a stale draft id is ignored rather than deleting or inventing a row`() {
        val ops = VariantDraftReconciler.plan(
            stored = stored,
            drafts = listOf(
                EditableVariantDraft(id = 11L, name = "Half", price = 120.0),
                EditableVariantDraft(id = 12L, name = "Full", price = 200.0),
                EditableVariantDraft(id = 99L, name = "Ghost", price = 10.0)
            )
        )

        assertTrue(ops.isEmpty())
    }
}
