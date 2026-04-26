CREATE TABLE IF NOT EXISTS customer_orders (
    id                  BIGSERIAL PRIMARY KEY,
    restaurant_id       BIGINT         NOT NULL,
    public_order_code   VARCHAR(64)    NOT NULL,
    tracking_token      VARCHAR(64)    NOT NULL,
    customer_name       VARCHAR(120)   NOT NULL,
    customer_phone      VARCHAR(20),
    customer_note       TEXT,
    fulfillment_type    VARCHAR(32)    NOT NULL,
    order_status        VARCHAR(32)    NOT NULL,
    payment_status      VARCHAR(32)    NOT NULL,
    payment_method      VARCHAR(32)    NOT NULL,
    source_channel      VARCHAR(32)    NOT NULL,
    currency            VARCHAR(8)     NOT NULL,
    subtotal            NUMERIC(12,2)  NOT NULL,
    total_amount        NUMERIC(12,2)  NOT NULL,
    created_at          BIGINT         NOT NULL,
    updated_at          BIGINT         NOT NULL,
    CONSTRAINT uq_customer_orders_public_code UNIQUE (public_order_code),
    CONSTRAINT uq_customer_orders_tracking_token UNIQUE (tracking_token)
);

CREATE INDEX IF NOT EXISTS idx_customer_orders_restaurant_created
    ON customer_orders (restaurant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_customer_orders_tracking_token
    ON customer_orders (tracking_token);

CREATE TABLE IF NOT EXISTS customer_order_items (
    id                  BIGSERIAL PRIMARY KEY,
    customer_order_id   BIGINT         NOT NULL,
    menu_item_id        BIGINT,
    item_variant_id     BIGINT,
    item_name           VARCHAR(255)   NOT NULL,
    variant_name        VARCHAR(255),
    quantity            INTEGER        NOT NULL,
    unit_price          NUMERIC(12,2)  NOT NULL,
    line_total          NUMERIC(12,2)  NOT NULL,
    special_instruction TEXT,
    created_at          BIGINT         NOT NULL,
    CONSTRAINT fk_customer_order_items_order
        FOREIGN KEY (customer_order_id) REFERENCES customer_orders(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_order_items_order
    ON customer_order_items (customer_order_id);
