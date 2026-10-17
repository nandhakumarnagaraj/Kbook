-- Add kitchen printer fields to restaurant_profiles so that both the customer
-- receipt printer and the kitchen ticket printer are persisted server-side.
-- Previously only a single (customer) printer was stored; the kitchen printer
-- config lived only on-device and was lost on reinstall / new device login.

ALTER TABLE restaurantprofiles
    ADD COLUMN IF NOT EXISTS kitchen_printer_enabled  BOOLEAN      DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS kitchen_printer_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kitchen_printer_mac      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS kitchen_printer_paper_size VARCHAR(10) DEFAULT '58mm';
