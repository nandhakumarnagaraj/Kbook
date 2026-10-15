-- Favourite (pinned) individual dishes.
--
-- Additive and NOT NULL DEFAULT FALSE so every existing row, and every client build
-- released before this column existed, reads as "not a favourite".
--
-- Server-authoritative, exactly like categories.is_favourite (V108), but the mechanism is
-- different because MenuItem DOES carry a field mask: the pin is restored in
-- GenericSyncService.applyChangedFieldsMerge (for a masked push that does not name it) and
-- again in preserveServerOwnedState (for a legacy whole-record push, and for a mask that
-- does name it). A terminal therefore cannot set it, and cannot clear it by omitting it or
-- by sending a stale copy. The only writer is the owner-gated favourite endpoint.
--
ALTER TABLE menuitems
    ADD COLUMN IF NOT EXISTS is_favourite BOOLEAN NOT NULL DEFAULT FALSE;

-- Index: the counter orders "favourites first, then A-Z" inside a category, which is the
-- query the till runs on every category tap. Declared after the column it indexes, so the
-- migration is replayable on a database that has never seen either.
CREATE INDEX IF NOT EXISTS idx_menuitems_tenant_category_fav
    ON menuitems (restaurant_id, category_id, is_favourite);
