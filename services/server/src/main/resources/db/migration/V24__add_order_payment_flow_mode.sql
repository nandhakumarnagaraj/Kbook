ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS order_payment_flow_mode VARCHAR(32) NOT NULL DEFAULT 'pay_before_food';

UPDATE restaurantprofiles
SET order_payment_flow_mode = 'pay_before_food'
WHERE order_payment_flow_mode IS NULL OR order_payment_flow_mode = '';
