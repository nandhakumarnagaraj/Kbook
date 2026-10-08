package com.khanabook.lite.pos.feature.menu.data

/**
 * Decides whether a record pulled from the server may overwrite the local row.
 *
 * The pull is normally server-authoritative, but a row that is still dirty (`is_synced = 0`)
 * is holding edits this device has not managed to push yet. Overwriting a dirty row with a
 * pulled entity used to erase the edit twice over: the pulled entity carries `is_synced = true`
 * and is built without a `changed_fields` mask, so the whole-row write both reverted the edit
 * AND marked the row acknowledged, removing it from `getUnsyncedMenuItems`. The edit was then
 * neither on the device nor on the server - it could disappear seconds after being saved,
 * whenever a sync that was already in flight pulled that row back.
 *
 * So a dirty row is only protected against an OLDER server version. When the pulled record is
 * STRICTLY NEWER (`pulled.updatedAt > existing.updatedAt`), the server already has a newer
 * version - typically because another terminal/web-admin edited it, or the push was rejected
 * with "Incoming record is older than the server record". Keeping the stale local copy in that
 * case re-pushes it forever (the loop seen on terminal 66: localIds 108, 551, 552, 553 rejected
 * every cycle, recovery pull never converged). LWW says the newer server row wins, so we adopt
 * it: the write marks the row synced and it drops out of the unsynced set.
 *
 * Returning null means "leave the local row exactly as it is". This is only done when the local
 * row is dirty AND its `updatedAt` is not older than the server's, i.e. the local edit is at
 * least as new as anything the server has - in which case overwriting could still revert an
 * unpushed change. Rows always converge: the newer side wins on the next push/pull.
 */
internal object MenuPullMergePolicy {

    /** The row to persist, or null to leave the dirty local row untouched. */
    fun resolveMenuItem(pulled: MenuItemEntity, existing: MenuItemEntity): MenuItemEntity? {
        if (!existing.isSynced && existing.updatedAt >= pulled.updatedAt) return null
        return pulled.copy(id = existing.id, isDeleted = pulled.isDeleted)
    }

    fun resolveVariant(pulled: ItemVariantEntity, existing: ItemVariantEntity): ItemVariantEntity? {
        if (!existing.isSynced && existing.updatedAt >= pulled.updatedAt) return null
        return pulled.copy(id = existing.id, isDeleted = pulled.isDeleted)
    }

    fun resolveCategory(pulled: CategoryEntity, existing: CategoryEntity): CategoryEntity? {
        if (!existing.isSynced && existing.updatedAt >= pulled.updatedAt) return null
        return pulled.copy(id = existing.id, isDeleted = pulled.isDeleted)
    }
}
