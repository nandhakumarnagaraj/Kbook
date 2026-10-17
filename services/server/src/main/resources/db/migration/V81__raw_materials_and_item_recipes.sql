-- Inventory deepening: raw materials + per-item recipe mapping (Phase 3)
CREATE TABLE IF NOT EXISTS raw_materials (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    unit VARCHAR(20) NOT NULL DEFAULT 'kg',
    stock_quantity NUMERIC(12,4) NOT NULL DEFAULT 0,
    low_stock_threshold NUMERIC(12,4) NOT NULL DEFAULT 0,
    cost_per_unit NUMERIC(12,2),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_raw_materials_restaurant ON raw_materials(restaurant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_raw_materials_restaurant_name ON raw_materials(restaurant_id, name);

CREATE TABLE IF NOT EXISTS item_recipes (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL,
    raw_material_id BIGINT NOT NULL REFERENCES raw_materials(id),
    quantity_per_item NUMERIC(12,4) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_item_recipes_restaurant ON item_recipes(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_item_recipes_menu_item ON item_recipes(restaurant_id, menu_item_id);
CREATE INDEX IF NOT EXISTS idx_item_recipes_material ON item_recipes(raw_material_id);

-- Idempotency flag: raw-material deduction runs once per bill
ALTER TABLE bills ADD COLUMN IF NOT EXISTS inventory_deducted BOOLEAN NOT NULL DEFAULT FALSE;
