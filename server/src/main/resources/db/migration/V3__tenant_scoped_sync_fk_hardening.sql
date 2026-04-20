CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_tenant_id ON categories(restaurant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_menuitems_tenant_id ON menuitems(restaurant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_itemvariants_tenant_id ON itemvariants(restaurant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_bills_tenant_id ON bills(restaurant_id, id);

-- Clear any existing server-side references that point to another tenant before
-- tightening constraints. Current production checks show zero bad rows, but this
-- keeps the migration self-healing for future rollouts.
UPDATE menuitems m
SET server_category_id = NULL
WHERE server_category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM categories c
      WHERE c.id = m.server_category_id
        AND c.restaurant_id = m.restaurant_id
  );

UPDATE itemvariants v
SET server_menu_item_id = NULL
WHERE server_menu_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM menuitems m
      WHERE m.id = v.server_menu_item_id
        AND m.restaurant_id = v.restaurant_id
  );

UPDATE bill_items bi
SET server_menu_item_id = NULL
WHERE server_menu_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM menuitems m
      WHERE m.id = bi.server_menu_item_id
        AND m.restaurant_id = bi.restaurant_id
  );

UPDATE bill_items bi
SET server_variant_id = NULL
WHERE server_variant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM itemvariants v
      WHERE v.id = bi.server_variant_id
        AND v.restaurant_id = bi.restaurant_id
  );

DELETE FROM bill_items bi
WHERE NOT EXISTS (
    SELECT 1
    FROM bills b
    WHERE b.id = bi.bill_id
      AND b.restaurant_id = bi.restaurant_id
);

DELETE FROM bill_payments bp
WHERE NOT EXISTS (
    SELECT 1
    FROM bills b
    WHERE b.id = bp.bill_id
      AND b.restaurant_id = bp.restaurant_id
);

UPDATE stock_logs sl
SET server_menu_item_id = NULL
WHERE server_menu_item_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM menuitems m
      WHERE m.id = sl.server_menu_item_id
        AND m.restaurant_id = sl.restaurant_id
  );

UPDATE stock_logs sl
SET server_variant_id = NULL
WHERE server_variant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM itemvariants v
      WHERE v.id = sl.server_variant_id
        AND v.restaurant_id = sl.restaurant_id
  );

ALTER TABLE menuitems
    DROP CONSTRAINT IF EXISTS fk_menuitems_category;

ALTER TABLE itemvariants
    DROP CONSTRAINT IF EXISTS fk_itemvariants_menuitem;

ALTER TABLE bill_items
    DROP CONSTRAINT IF EXISTS fk_bill_items_bill;

ALTER TABLE bill_items
    DROP CONSTRAINT IF EXISTS fk_bill_items_menuitem;

ALTER TABLE bill_items
    DROP CONSTRAINT IF EXISTS fk_bill_items_variant;

ALTER TABLE bill_payments
    DROP CONSTRAINT IF EXISTS fk_bill_payments_bill;

ALTER TABLE stock_logs
    DROP CONSTRAINT IF EXISTS fk_stock_logs_menuitem;

ALTER TABLE stock_logs
    DROP CONSTRAINT IF EXISTS fk_stock_logs_variant;

ALTER TABLE menuitems
    ADD CONSTRAINT fk_menuitems_category_tenant
    FOREIGN KEY (restaurant_id, server_category_id)
    REFERENCES categories(restaurant_id, id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED
    NOT VALID;

ALTER TABLE itemvariants
    ADD CONSTRAINT fk_itemvariants_menuitem_tenant
    FOREIGN KEY (restaurant_id, server_menu_item_id)
    REFERENCES menuitems(restaurant_id, id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED
    NOT VALID;

ALTER TABLE bill_items
    ADD CONSTRAINT fk_bill_items_bill_tenant
    FOREIGN KEY (restaurant_id, bill_id)
    REFERENCES bills(restaurant_id, id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE bill_items
    ADD CONSTRAINT fk_bill_items_menuitem_tenant
    FOREIGN KEY (restaurant_id, server_menu_item_id)
    REFERENCES menuitems(restaurant_id, id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED
    NOT VALID;

ALTER TABLE bill_items
    ADD CONSTRAINT fk_bill_items_variant_tenant
    FOREIGN KEY (restaurant_id, server_variant_id)
    REFERENCES itemvariants(restaurant_id, id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED
    NOT VALID;

ALTER TABLE bill_payments
    ADD CONSTRAINT fk_bill_payments_bill_tenant
    FOREIGN KEY (restaurant_id, bill_id)
    REFERENCES bills(restaurant_id, id)
    ON DELETE CASCADE
    NOT VALID;

ALTER TABLE stock_logs
    ADD CONSTRAINT fk_stock_logs_menuitem_tenant
    FOREIGN KEY (restaurant_id, server_menu_item_id)
    REFERENCES menuitems(restaurant_id, id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED
    NOT VALID;

ALTER TABLE stock_logs
    ADD CONSTRAINT fk_stock_logs_variant_tenant
    FOREIGN KEY (restaurant_id, server_variant_id)
    REFERENCES itemvariants(restaurant_id, id)
    ON DELETE RESTRICT
    DEFERRABLE INITIALLY DEFERRED
    NOT VALID;

ALTER TABLE menuitems VALIDATE CONSTRAINT fk_menuitems_category_tenant;
ALTER TABLE itemvariants VALIDATE CONSTRAINT fk_itemvariants_menuitem_tenant;
ALTER TABLE bill_items VALIDATE CONSTRAINT fk_bill_items_bill_tenant;
ALTER TABLE bill_items VALIDATE CONSTRAINT fk_bill_items_menuitem_tenant;
ALTER TABLE bill_items VALIDATE CONSTRAINT fk_bill_items_variant_tenant;
ALTER TABLE bill_payments VALIDATE CONSTRAINT fk_bill_payments_bill_tenant;
ALTER TABLE stock_logs VALIDATE CONSTRAINT fk_stock_logs_menuitem_tenant;
ALTER TABLE stock_logs VALIDATE CONSTRAINT fk_stock_logs_variant_tenant;
