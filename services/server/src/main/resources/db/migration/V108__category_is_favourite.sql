-- Favourite (pinned) menu categories.
--
-- Additive and NOT NULL DEFAULT FALSE so every existing row, and every client build
-- released before this column existed, reads as "not a favourite".
--
-- Server-authoritative: GenericSyncService.preserveServerOwnedState restores the stored
-- value on every push, so a whole-record category push from a terminal (rename, reorder,
-- active toggle) cannot clear a pin it does not know about. The only mutation path is the
-- owner-gated favourite endpoint.
ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS is_favourite BOOLEAN NOT NULL DEFAULT FALSE;
