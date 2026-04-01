-- ============================================================
-- V8: Production Hardening
-- ============================================================

-- ============================================================
-- 1. OPTIMISTIC LOCKING — version columns on all sync tables
-- ============================================================
ALTER TABLE users               ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE restaurantprofiles  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE categories          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE menuitems           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE itemvariants        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bills               ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bill_items          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bill_payments       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE stock_logs          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- 2. FOREIGN KEY CONSTRAINTS on server_*_id columns
--    All DEFERRABLE INITIALLY DEFERRED so batch inserts in
--    the same transaction don't trip constraint checks mid-batch.
-- ============================================================
ALTER TABLE menuitems
    ADD CONSTRAINT fk_menuitems_category
    FOREIGN KEY (server_category_id)
    REFERENCES categories(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE itemvariants
    ADD CONSTRAINT fk_itemvariants_menuitem
    FOREIGN KEY (server_menu_item_id)
    REFERENCES menuitems(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE bill_items
    ADD CONSTRAINT fk_bill_items_bill
    FOREIGN KEY (server_bill_id)
    REFERENCES bills(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE bill_items
    ADD CONSTRAINT fk_bill_items_menuitem
    FOREIGN KEY (server_menu_item_id)
    REFERENCES menuitems(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE bill_items
    ADD CONSTRAINT fk_bill_items_variant
    FOREIGN KEY (server_variant_id)
    REFERENCES itemvariants(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE bill_payments
    ADD CONSTRAINT fk_bill_payments_bill
    FOREIGN KEY (server_bill_id)
    REFERENCES bills(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE stock_logs
    ADD CONSTRAINT fk_stock_logs_menuitem
    FOREIGN KEY (server_menu_item_id)
    REFERENCES menuitems(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE stock_logs
    ADD CONSTRAINT fk_stock_logs_variant
    FOREIGN KEY (server_variant_id)
    REFERENCES itemvariants(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

-- ============================================================
-- 3. CHECK CONSTRAINTS — enforce business rules at DB level
-- ============================================================
ALTER TABLE bills
    ADD CONSTRAINT chk_bills_subtotal     CHECK (subtotal >= 0),
    ADD CONSTRAINT chk_bills_total        CHECK (total_amount >= 0);

ALTER TABLE bill_items
    ADD CONSTRAINT chk_bill_items_quantity CHECK (quantity > 0),
    ADD CONSTRAINT chk_bill_items_price    CHECK (price >= 0),
    ADD CONSTRAINT chk_bill_items_total    CHECK (item_total >= 0);

ALTER TABLE menuitems
    ADD CONSTRAINT chk_menuitems_price     CHECK (base_price >= 0);

ALTER TABLE itemvariants
    ADD CONSTRAINT chk_itemvariants_price  CHECK (price >= 0);

ALTER TABLE bill_payments
    ADD CONSTRAINT chk_bill_payments_amount CHECK (amount >= 0);

ALTER TABLE users
    ADD CONSTRAINT chk_users_auth_provider
    CHECK (auth_provider IN ('PHONE', 'GOOGLE'));

-- ============================================================
-- 4. MISSING INDEXES
-- ============================================================

-- Soft-delete composite indexes (every tenant query filters is_deleted)
CREATE INDEX idx_bills_tenant_deleted          ON bills(restaurant_id, is_deleted);
CREATE INDEX idx_menuitems_tenant_deleted      ON menuitems(restaurant_id, is_deleted);
CREATE INDEX idx_categories_tenant_deleted     ON categories(restaurant_id, is_deleted);
CREATE INDEX idx_itemvariants_tenant_deleted   ON itemvariants(restaurant_id, is_deleted);
CREATE INDEX idx_bill_items_tenant_deleted     ON bill_items(restaurant_id, is_deleted);
CREATE INDEX idx_bill_payments_tenant_deleted  ON bill_payments(restaurant_id, is_deleted);
CREATE INDEX idx_stock_logs_tenant_deleted     ON stock_logs(restaurant_id, is_deleted);

-- FK join indexes (partial — only where the FK is actually set)
CREATE INDEX idx_menuitems_server_category      ON menuitems(server_category_id)      WHERE server_category_id IS NOT NULL;
CREATE INDEX idx_itemvariants_server_menuitem   ON itemvariants(server_menu_item_id)  WHERE server_menu_item_id IS NOT NULL;
CREATE INDEX idx_bill_items_server_bill         ON bill_items(server_bill_id)         WHERE server_bill_id IS NOT NULL;
CREATE INDEX idx_bill_items_server_menuitem     ON bill_items(server_menu_item_id)    WHERE server_menu_item_id IS NOT NULL;
CREATE INDEX idx_bill_items_server_variant      ON bill_items(server_variant_id)      WHERE server_variant_id IS NOT NULL;
CREATE INDEX idx_bill_payments_server_bill      ON bill_payments(server_bill_id)      WHERE server_bill_id IS NOT NULL;
CREATE INDEX idx_stock_logs_server_menuitem     ON stock_logs(server_menu_item_id)    WHERE server_menu_item_id IS NOT NULL;
CREATE INDEX idx_stock_logs_server_variant      ON stock_logs(server_variant_id)      WHERE server_variant_id IS NOT NULL;

-- menu_extraction_jobs had no indexes at all
CREATE INDEX idx_menu_extraction_jobs_restaurant ON menu_extraction_jobs(restaurant_id);
CREATE INDEX idx_menu_extraction_jobs_status     ON menu_extraction_jobs(status);

-- OTP cleanup and phone lookup
CREATE INDEX idx_otp_requests_phone              ON otp_requests(phone_number);

-- Bills: report queries by creation time
CREATE INDEX idx_bills_tenant_created            ON bills(restaurant_id, created_at);

-- Server-updated-at composite for efficient pull queries
CREATE INDEX idx_bills_tenant_server_updated         ON bills(restaurant_id, server_updated_at);
CREATE INDEX idx_menuitems_tenant_server_updated     ON menuitems(restaurant_id, server_updated_at);
CREATE INDEX idx_categories_tenant_server_updated    ON categories(restaurant_id, server_updated_at);
CREATE INDEX idx_itemvariants_tenant_server_updated  ON itemvariants(restaurant_id, server_updated_at);
CREATE INDEX idx_bill_items_tenant_server_updated    ON bill_items(restaurant_id, server_updated_at);
CREATE INDEX idx_bill_payments_tenant_server_updated ON bill_payments(restaurant_id, server_updated_at);
CREATE INDEX idx_stock_logs_tenant_server_updated    ON stock_logs(restaurant_id, server_updated_at);
CREATE INDEX idx_users_tenant_server_updated         ON users(restaurant_id, server_updated_at);

-- ============================================================
-- 5. TOKEN BLOCKLIST TABLE — JWT revocation on logout
-- ============================================================
CREATE TABLE token_blocklist (
    jti        VARCHAR(64) NOT NULL PRIMARY KEY,
    expires_at BIGINT      NOT NULL,
    revoked_at BIGINT      NOT NULL
);
CREATE INDEX idx_token_blocklist_expires ON token_blocklist(expires_at);

-- ============================================================
-- 6. FIX daily_order_counter reset logic in restaurantprofiles
--    Adds a proper DATE column alongside VARCHAR for migration path.
--    The service layer uses last_reset_date_proper going forward.
-- ============================================================
ALTER TABLE restaurantprofiles ADD COLUMN last_reset_date_proper DATE;
UPDATE restaurantprofiles
    SET last_reset_date_proper = last_reset_date::DATE
    WHERE last_reset_date IS NOT NULL
      AND last_reset_date ~ '^\d{4}-\d{2}-\d{2}$';
