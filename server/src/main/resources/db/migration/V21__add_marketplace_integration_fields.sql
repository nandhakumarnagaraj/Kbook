-- Add API credentials and webhook secrets to restaurantprofiles
ALTER TABLE restaurantprofiles
ADD COLUMN IF NOT EXISTS zomato_api_key VARCHAR(255),
ADD COLUMN IF NOT EXISTS zomato_webhook_secret VARCHAR(255),
ADD COLUMN IF NOT EXISTS swiggy_api_key VARCHAR(255),
ADD COLUMN IF NOT EXISTS swiggy_webhook_secret VARCHAR(255);

-- Create marketplace_orders table
CREATE TABLE IF NOT EXISTS marketplace_orders (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    bill_id BIGINT,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('ZOMATO', 'SWIGGY')),
    platform_order_id VARCHAR(255) NOT NULL,
    platform_status VARCHAR(50),
    customer_name VARCHAR(255),
    customer_phone VARCHAR(50),
    customer_address TEXT,
    subtotal NUMERIC(12,2),
    tax_amount NUMERIC(12,2),
    total_amount NUMERIC(12,2) NOT NULL,
    payment_mode VARCHAR(50),
    raw_payload TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    synced_at BIGINT,
    CONSTRAINT fk_marketplace_orders_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurantprofiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_marketplace_orders_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE SET NULL,
    CONSTRAINT uk_marketplace_order_platform_order_id UNIQUE (platform, platform_order_id)
);

CREATE INDEX IF NOT EXISTS idx_marketplace_orders_restaurant ON marketplace_orders(restaurant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_marketplace_orders_bill ON marketplace_orders(bill_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_orders_platform ON marketplace_orders(platform, platform_order_id);

-- Create marketplace_order_items table
CREATE TABLE IF NOT EXISTS marketplace_order_items (
    id BIGSERIAL PRIMARY KEY,
    marketplace_order_id BIGINT NOT NULL,
    bill_item_id BIGINT,
    platform_item_id VARCHAR(255),
    item_name VARCHAR(255) NOT NULL,
    variant_name VARCHAR(255),
    price NUMERIC(12,2) NOT NULL,
    quantity INTEGER NOT NULL,
    item_total NUMERIC(12,2) NOT NULL,
    special_instruction TEXT,
    CONSTRAINT fk_marketplace_order_items_order FOREIGN KEY (marketplace_order_id) REFERENCES marketplace_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_marketplace_order_items_bill_item FOREIGN KEY (bill_item_id) REFERENCES bill_items(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_marketplace_order_items_order ON marketplace_order_items(marketplace_order_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_order_items_bill_item ON marketplace_order_items(bill_item_id);
