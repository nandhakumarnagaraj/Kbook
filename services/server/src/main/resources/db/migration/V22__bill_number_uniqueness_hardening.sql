DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM bills
        WHERE is_deleted = false
          AND lifetime_order_id IS NOT NULL
        GROUP BY restaurant_id, lifetime_order_id
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS ux_bills_restaurant_lifetime_order_active
            ON bills (restaurant_id, lifetime_order_id)
            WHERE is_deleted = false;
    ELSE
        RAISE NOTICE 'Skipping ux_bills_restaurant_lifetime_order_active because duplicate active invoices already exist.';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM bills
        WHERE is_deleted = false
          AND last_reset_date IS NOT NULL
          AND daily_order_id IS NOT NULL
        GROUP BY restaurant_id, last_reset_date, daily_order_id
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS ux_bills_restaurant_daily_order_active
            ON bills (restaurant_id, last_reset_date, daily_order_id)
            WHERE is_deleted = false;
    ELSE
        RAISE NOTICE 'Skipping ux_bills_restaurant_daily_order_active because duplicate active daily order numbers already exist.';
    END IF;
END $$;
