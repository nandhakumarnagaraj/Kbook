-- Plan 05: complete inventory loop — vendors, movements ledger,
-- purchase orders, material expiry
CREATE TABLE IF NOT EXISTS vendors (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(20),
    notes TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_vendors_restaurant ON vendors(restaurant_id);

CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    raw_material_id BIGINT NOT NULL REFERENCES raw_materials(id),
    kind VARCHAR(20) NOT NULL,          -- PURCHASE|WASTAGE|SALES_DEDUCT|ADJUST|OPENING
    quantity NUMERIC(12,4) NOT NULL,    -- positive = in, negative = out
    unit_cost NUMERIC(12,2),
    vendor_id BIGINT,
    bill_id BIGINT,
    reason TEXT,
    created_by_user_id BIGINT,
    created_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_stock_movements_lookup
    ON stock_movements(restaurant_id, raw_material_id, created_at);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    vendor_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT|SENT|RECEIVED|CANCELLED
    note TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_restaurant ON purchase_orders(restaurant_id);

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id BIGSERIAL PRIMARY KEY,
    po_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    raw_material_id BIGINT NOT NULL REFERENCES raw_materials(id),
    quantity NUMERIC(12,4) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_po_items_po ON purchase_order_items(po_id);

ALTER TABLE raw_materials ADD COLUMN IF NOT EXISTS expiry_date BIGINT;
