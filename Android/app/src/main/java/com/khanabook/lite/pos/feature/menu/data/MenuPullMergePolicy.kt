package com.khanabook.lite.pos.feature.menu.data

/**
 * Decides whether a record pulled from the server may overwrite the local row.
 *
 * The pull is normally server-authoritative, but a row that is still dirty (`is_synced = 0`)
 * is holding edits this device has not managed to push yet. Overwriting it used to erase
 * them twice over: the pulled entity carries `is_synced = true` and is built without a
 * `changed_fields` mask, so the whole-row write both reverted the edit AND marked the row
 * acknowledged, which removed it from `getUnsyncedMenuItems`. The edit was then neither on
 * the device nor on the server - it could disappear seconds after being saved, whenever a
 * sync that was already in flight pulled that row back.
 *
 * Returning null means "leave the local row exactly as it is". Deferring is safe, because a
 * row always converges:
 *  - if the push succeeds, the row stops being dirty and the next pull applies server state;
 *  - if the push is rejected, quarantine calls `markMenuItemsAsSynced`, which sets
 *    `is_synced = 1` and `changed_fields = NULL`, releasing the row to the pull.
 * So this delays the server's version by at most one sync cycle and can never diverge forever.
 */
internal object MenuPullMergePolicy {

    /** The row to persist, or null to leave the dirty local row untouched. */
    fun resolveMenuItem(pulled: MenuItemEntity, existing: MenuItemEntity): MenuItemEntity? {
        if (!existing.isSynced) return null
        return pulled.copy(id = existing.id, isDeleted = pulled.isDeleted)
    }

    fun resolveVariant(pulled: ItemVariantEntity, existing: ItemVariantEntity): ItemVariantEntity? {
        if (!existing.isSynced) return null
        return pulled.copy(id = existing.id, isDeleted = pulled.isDeleted)
    }

    fun resolveCategory(pulled: CategoryEntity, existing: CategoryEntity): CategoryEntity? {
        if (!existing.isSynced) return null
        return pulled.copy(id = existing.id, isDeleted = pulled.isDeleted)
    }
}
