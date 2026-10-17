-- Explicit variant mode for menu items.
--
-- Until now a menu item's pricing mode was INFERRED: a blank or zero base_price meant
-- "this is a container for variants and has no price of its own". Every caller had to
-- guess, and ItemEditDialog guessing wrong is what crashed the save dialog
-- (see docs/reviews/KHANABOOK_MENU_PRICE_SYNC_DATA_LOSS_2026-09-27.md).
--
-- base_price stays NOT NULL. Every competitor in the reference set keeps a non-null
-- parent price and puts the nullability on the cart line instead; see
-- docs/design/MENU_ITEM_MODEL_INDIA_FIT_GAP.md section 2.

ALTER TABLE menuitems
    ADD COLUMN IF NOT EXISTS has_variants BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill from reality rather than trusting the default. An item is a variant
-- container if any of its variants are still live. Rows for variants whose parent
-- has since been hard-deleted simply do not contribute, which is the safe direction:
-- a stale FALSE on a real container is recoverable, a stale TRUE on a simple item
-- would render a meaningless "from" price.
UPDATE menuitems m
SET has_variants = TRUE
WHERE EXISTS (
    SELECT 1
    FROM itemvariants v
    WHERE v.menu_item_id = m.id
      AND v.is_deleted = FALSE
);

CREATE INDEX IF NOT EXISTS idx_menuitems_has_variants
    ON menuitems (restaurant_id, has_variants);
